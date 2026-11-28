package qouteall.imm_ptl.core.compat.mixin.sodium;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.q_misc_util.Helper;

import java.util.Map;

@Mixin(value = RenderRegion.class, remap = false)
public class MixinSodiumRenderRegion {
    @Unique
    private @Nullable ObjectArrayList<ChunkRenderList> chunkRenderListsForPortalRendering = null;
    
    @Unique
    private @Nullable ObjectArrayList<Map<TerrainRenderPass, MultiDrawBatch>> cachedBatchesForPortalRendering = null;
    
    /**
     * The world rendering process is as follows:
     * 1. Render solid things.
     * 2. Render portals recursively, which increases Sodium's frame counter.
     * 3. Render transparent things.
     * A same-world portal render resets the region's ChunkRenderList in the new frame,
     * which breaks the transparent rendering of the outer world.
     * So each portal rendering layer uses its own ChunkRenderList.
     */
    @Inject(method = "getRenderList", at = @At("HEAD"), cancellable = true)
    private void onGetRenderList(CallbackInfoReturnable<ChunkRenderList> cir) {
        if (!PortalRendering.isRendering()) {
            return;
        }
        
        RenderRegion this_ = (RenderRegion) (Object) this;
        
        if (chunkRenderListsForPortalRendering == null) {
            chunkRenderListsForPortalRendering = new ObjectArrayList<>();
        }
        
        int index = PortalRendering.getPortalLayer() - 1;
        
        cir.setReturnValue(Helper.arrayListComputeIfAbsent(
            chunkRenderListsForPortalRendering,
            index,
            () -> new ChunkRenderList(this_)
        ));
    }
    
    @Unique
    private Map<TerrainRenderPass, MultiDrawBatch> getPortalCachedBatches() {
        if (cachedBatchesForPortalRendering == null) {
            cachedBatchesForPortalRendering = new ObjectArrayList<>();
        }
        
        int index = PortalRendering.getPortalLayer() - 1;
        return Helper.arrayListComputeIfAbsent(
            cachedBatchesForPortalRendering,
            index,
            Reference2ReferenceOpenHashMap::new
        );
    }
    
    /**
     * The cached MultiDrawBatch is part of the same per-frame state as the ChunkRenderList.
     * A recursive same-world portal render would otherwise overwrite the batch that the outer render still needs.
     */
    @Inject(method = "getCachedBatch", at = @At("HEAD"), cancellable = true)
    private void onGetCachedBatch(TerrainRenderPass pass, CallbackInfoReturnable<MultiDrawBatch> cir) {
        if (!PortalRendering.isRendering()) {
            return;
        }
        
        Map<TerrainRenderPass, MultiDrawBatch> map = getPortalCachedBatches();
        MultiDrawBatch batch = map.get(pass);
        if (batch == null) {
            batch = new MultiDrawBatch((ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE) + 1);
            map.put(pass, batch);
        }
        cir.setReturnValue(batch);
    }
    
    /**
     * A rebuilt or uploaded section changes the GPU buffer that every layer's cached batch refers to.
     * So the batches of all layers must be cleared, not only the active one.
     */
    @Inject(method = "clearCachedBatchFor", at = @At("HEAD"))
    private void onClearCachedBatchFor(TerrainRenderPass pass, CallbackInfo ci) {
        if (cachedBatchesForPortalRendering == null) {
            return;
        }
        for (Map<TerrainRenderPass, MultiDrawBatch> map : cachedBatchesForPortalRendering) {
            if (map == null) {
                continue;
            }
            MultiDrawBatch batch = map.get(pass);
            if (batch != null) {
                batch.clear();
            }
        }
    }
    
    @Inject(method = "clearAllCachedBatches", at = @At("HEAD"))
    private void onClearAllCachedBatches(CallbackInfo ci) {
        if (cachedBatchesForPortalRendering == null) {
            return;
        }
        for (Map<TerrainRenderPass, MultiDrawBatch> map : cachedBatchesForPortalRendering) {
            if (map == null) {
                continue;
            }
            for (MultiDrawBatch batch : map.values()) {
                batch.clear();
            }
        }
    }
    
    @Inject(method = "delete", at = @At("HEAD"))
    private void onDelete(CommandList commandList, CallbackInfo ci) {
        if (cachedBatchesForPortalRendering != null) {
            for (Map<TerrainRenderPass, MultiDrawBatch> map : cachedBatchesForPortalRendering) {
                if (map == null) {
                    continue;
                }
                for (MultiDrawBatch batch : map.values()) {
                    batch.delete();
                }
            }
            cachedBatchesForPortalRendering = null;
        }
        
        chunkRenderListsForPortalRendering = null;
    }
}
