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

@Overwrite
public MultiDrawBatch getCachedBatch(TerrainRenderPass pass) {
    var map = getActiveCachedBatches();
    var batch = map.get(pass);
    if (batch != null) return batch;
    batch = new MultiDrawBatch((ModelQuadFacing.COUNT * RenderRegion.REGION_SIZE) + 1);
    map.put(pass, batch);
    return batch;
}

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