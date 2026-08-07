package qouteall.imm_ptl.core.compat.sodium_compatibility;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;

/**
 * ChunkJob (the type RenderSection#getRunningJob() returns) doesn't expose which
 * concrete task it wraps. We need this to tell a rebuild (ChunkBuilderMeshingTask)
 * apart from a sort (ChunkBuilderSortingTask) for a section's currently-running job,
 * so that ip_forceBlockingSortCatchUp() can skip resorting sections that have a
 * rebuild in flight without also skipping sections that merely have an ordinary
 * sort in flight/pending.
 */
public interface IEChunkJobTyped {
    ChunkBuilderTask ip_getTask();
}
