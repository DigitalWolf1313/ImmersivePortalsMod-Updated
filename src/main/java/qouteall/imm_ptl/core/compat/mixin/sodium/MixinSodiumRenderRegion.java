package qouteall.imm_ptl.core.compat.mixin.sodium;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.q_misc_util.Helper;

import java.util.Map;

@Mixin(value = RenderRegion.class, remap = false)
public class MixinSodiumRenderRegion {
    @Shadow @Final private ChunkRenderList renderList;
    @Shadow @Final private Map<TerrainRenderPass, MultiDrawBatch> cachedBatches;

    @Unique
    private @Nullable ObjectArrayList<ChunkRenderList> chunkRenderListsForPortalRendering = null;

    @Unique
    private @Nullable ObjectArrayList<Map<TerrainRenderPass, MultiDrawBatch>> cachedBatchesForPortalRendering = null;

    /**
    * @author DigitalWolf13
    * @reason iPortals renders the same world multiple times within a frame:
    * 1. render solid geometry
    * 2. recursively render portals (advancing Sodium's frame state)
    * 3. render transparent geometry
    *
    * For same-world portals, the recursive render advances the frame counter before
    * the outer world's transparent pass finishes. Sodium then rebuilds its per-frame
    * region state (ChunkRenderList and cached draw batches), invalidating the state
    * still needed by the outer render. Store separate instances for each portal
    * rendering layer so recursive renders cannot interfere with each other.
    */

    @Overwrite
    public ChunkRenderList getRenderList() {
        if (!PortalRendering.isRendering()) {
            return renderList;
        }

        RenderRegion this_ = (RenderRegion) (Object) this;

        if (chunkRenderListsForPortalRendering == null) {
            chunkRenderListsForPortalRendering = new ObjectArrayList<>();
        }

        int index = PortalRendering.getPortalLayer() - 1;

        return Helper.arrayListComputeIfAbsent(
            chunkRenderListsForPortalRendering,
            index,
            () -> new ChunkRenderList(this_)
        );
    }

    @Unique
    private Map<TerrainRenderPass, MultiDrawBatch> getActiveCachedBatches() {
        if (!PortalRendering.isRendering()) {
            return this.cachedBatches;
        }
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
    * @author DigitalWolf13
    * @reason Cached MultiDrawBatch instances are part of the same per-frame render
    * state as ChunkRenderList. They must also be separated by portal rendering layer,
    * otherwise a recursive same-world portal render can overwrite the batches still
    * required by the outer world render.
    */
@Overwrite
public MultiDrawBatch getCachedBatch(TerrainRenderPass pass) {
    var map = getActiveCachedBatches();
    var batch = map.get(pass);
    if (batch != null) return batch;
    batch = new MultiDrawBatch((ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE) + 1);
    map.put(pass, batch);
    return batch;
}

    /**
    * @author DigitalWolf13
    * @reason Cached batches may be invalidated because the underlying GPU resources
    * for a region were rebuilt. Since every portal layer keeps its own cached batch
    * but all reference the same uploaded region data, the cache must be cleared for
    * every layer, not only the currently active one.
    */
@Overwrite
public void clearCachedBatchFor(TerrainRenderPass pass) {
    // Can be triggered by a resource-level change (section rebuild/upload), which affects
    // the shared GPU buffer that EVERY portal layer's cached batch references — not just
    // whichever layer happens to be "active" right now. So clear it everywhere.
    clearOne(this.cachedBatches, pass);

    if (cachedBatchesForPortalRendering != null) {
        for (var map : cachedBatchesForPortalRendering) {
            if (map != null) clearOne(map, pass);
        }
    }
}

    /**
    * @author DigitalWolf13
    * @reason Every portal rendering layer maintains its own cached draw batches.
    * Clear them all so stale cached draw commands cannot survive across region resets.
    */
@Overwrite
public void clearAllCachedBatches() {
    for (var batch : this.cachedBatches.values()) {
        batch.clear();
    }
    if (cachedBatchesForPortalRendering != null) {
        for (var map : cachedBatchesForPortalRendering) {
            if (map == null) continue;
            for (var batch : map.values()) {
                batch.clear();
            }
        }
    }
}

    /**
     * Clean up portal-layer cached batches when the region is deleted, to prevent
     * memory leaks.
     */
    @Inject(method = "delete", at = @At("HEAD"))
    private void onDelete(CommandList commandList, CallbackInfo ci) {
        if (cachedBatchesForPortalRendering != null) {
            for (var map : cachedBatchesForPortalRendering) {
                if (map == null) continue;
                for (var batch : map.values()) {
                    batch.delete();
                }
            }
            cachedBatchesForPortalRendering = null;
        }

        if (chunkRenderListsForPortalRendering != null) {
            chunkRenderListsForPortalRendering = null;
        }
    }

@Unique
private static void clearOne(Map<TerrainRenderPass, MultiDrawBatch> map, TerrainRenderPass pass) {
    var batch = map.get(pass);
    if (batch != null) {
        batch.clear();
    }

}
}