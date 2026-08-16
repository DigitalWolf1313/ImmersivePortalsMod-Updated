package qouteall.imm_ptl.core.compat.mixin.sodium;

import java.util.concurrent.ConcurrentLinkedDeque;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkUpdateTypes;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkSortOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobTyped;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderSortingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.data.DynamicData;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import net.minecraft.client.multiplayer.ClientLevel;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.sodium_compatibility.IEChunkJobTyped;
import qouteall.imm_ptl.core.compat.sodium_compatibility.IESodiumRenderSectionManager;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumRenderingContext;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@Mixin(value = RenderSectionManager.class, remap = false)
public class MixinSodiumRenderSectionManager implements IESodiumRenderSectionManager {
    @Shadow
    @Final
    @Mutable
    private int renderDistance;
    
    @Shadow
    private @NotNull SortedRenderLists renderLists;

    @Shadow
    private @Nullable Vector3dc cameraPosition;
    
    @Shadow
    @Final
    private ChunkBuilder builder;
    
    @Shadow
    private int frame;
    
    @Shadow
    @Final
    private ConcurrentLinkedDeque<ChunkJobResult<? extends BuilderTaskOutput>> buildResults;
    
    @Shadow
    @Final
    private ClientLevel level;
    
    @Shadow
    public @Nullable ChunkBuilderSortingTask createSortTask(RenderSection render, int frame) { return null; }

    @Shadow
    public @Nullable ChunkBuilderMeshingTask createRebuildTask(RenderSection render, int frame) { return null; }
    
    @Shadow
    public void cleanupAndFlip() { }
    
    @Shadow
    public void uploadChunks() { }

    
    @Shadow
    public void onChunkAdded(int x, int z) { }
    
    @Override
    public void ip_swapContext(SodiumRenderingContext context) {
        Validate.isTrue(context.renderDistance != 0, "Render distance cannot be 0");
        Validate.isTrue(context.renderLists != null);
        
        SortedRenderLists renderListsTmp = renderLists;
        renderLists = context.renderLists;
        context.renderLists = renderListsTmp;
        
        int renderDistanceTmp = renderDistance;
        renderDistance = context.renderDistance;
        context.renderDistance = renderDistanceTmp;

        // Swap RenderSectionManager's own camera position too. This is a separate
        // field from SodiumWorldRenderer#lastCameraPos (swapped in SodiumInterface),
        // and feeds createRebuildTask/createSortTask/integrateTranslucentData/
        // applyTriggerChanges. Without swapping this too, an async chunk build or
        // sort result that gets processed while this field holds the portal's
        // virtual camera position (i.e. uploadChunks() runs during the portal's
        // setupTerrain() call) gets permanently sorted relative to the wrong camera.
        Vector3dc cameraPositionTmp = cameraPosition;
        cameraPosition = context.cameraPosition;
        context.cameraPosition = cameraPositionTmp;
        
    }
    
    /**
     * The section visibility information will be wrong if rendered a portal.
     * Just cancel this optimization.
     * isSectionVisible() is currently only used for culling entities.
     */
    @Inject(method = "isSectionVisible", at = @At("HEAD"), cancellable = true)
    private void onIsSectionVisible(int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (RenderStates.portalsRenderedThisFrame != 0) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Determines whether a pending/running job can be replaced by a forced
     * translucent sorting pass.
     *
     * Rebuild jobs are excluded because they produce fresh geometry and their
     * ordering data should not be superseded by a standalone sort task.
     */  
    private boolean shouldScheduleBlockingSortCatchUp(int pendingUpdate, Object runningJob) {
        boolean pendingIsRebuild =
            ChunkUpdateTypes.isRebuild(pendingUpdate);

        boolean runningIsRebuild = runningJob instanceof ChunkJobTyped
            && ((IEChunkJobTyped) runningJob).ip_getTask() instanceof ChunkBuilderMeshingTask;

        return !pendingIsRebuild && !runningIsRebuild;
    }

/**
 * Force a blocking sort catch-up for all currently-visible sections that have
 * dynamic translucency data. This is called both:
 * <ul>
 *     <li>after returning from a same-dimension portal render, when the outer
 *     camera's context has been restored but the GPU buffers may still be sorted
 *     for the portal's camera, and</li>
 *     <li>before rendering translucent geometry from within a portal's own render,
 *     when the GPU buffers may still be sorted for the enclosing (outer or parent
 *     portal) camera.</li>
 * </ul>
 */
@Override
public void ip_forceBlockingSortCatchUp() {
    double radius = IPGlobal.forceBlockingSortCatchUpRadius;
    boolean useRadiusCutoff = radius >= 0.0;
    float radiusSq = useRadiusCutoff ? (float) (radius * radius) : 0f;

    Vector3dc camPos = this.cameraPosition;
    boolean canCheckDistance = useRadiusCutoff && camPos != null;
    float camX = 0f, camY = 0f, camZ = 0f;
    if (canCheckDistance) {
        camX = (float) camPos.x();
        camY = (float) camPos.y();
        camZ = (float) camPos.z();
    }

    ChunkJobCollector collector = new ChunkJobCollector(result -> this.buildResults.add(result));

    {
        var iter = this.renderLists.iterator(false);
        while (iter.hasNext()) {
            ChunkRenderList list = iter.next();
            ByteIterator sectionIterator = list.sectionsWithGeometryIterator(false);
            if (sectionIterator == null) {
                continue;
            }
            while (sectionIterator.hasNext()) {
                int sectionIndex = sectionIterator.nextByteAsInt();
                RenderSection section = list.getRegion().getSection(sectionIndex);
                if (section == null || section.isDisposed() || !section.isBuilt()) {
                    continue;
                }

                var translucentData = section.getTranslucentData();
                if (!(translucentData instanceof DynamicData)) {
                    continue;
                }

                if (canCheckDistance && section.getSquaredDistance(camX, camY, camZ) > radiusSq) {
                    continue;
                }

                int pending = section.getPendingUpdate();
                var runningJob = section.getRunningJob();
                if (!this.shouldScheduleBlockingSortCatchUp(pending, runningJob)) {
                    continue;
                }

                ChunkBuilderSortingTask sortTask = this.createSortTask(section, this.frame);
                if (sortTask != null) {
                    ChunkJobTyped<ChunkBuilderSortingTask, ChunkSortOutput> job =
                        this.builder.scheduleTask(sortTask, true, collector::onJobFinished, true);
                    collector.addSubmittedJob(job);
                    section.setLastSubmittedFrame(this.frame);
                }
            }
        }
    }

    if (collector.getSubmittedTaskCount() == 0) {
        return;
    }

    collector.awaitCompletion(this.builder);

    this.cleanupAndFlip();
    this.uploadChunks();
}

    
}