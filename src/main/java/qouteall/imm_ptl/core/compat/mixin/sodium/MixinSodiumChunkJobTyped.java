package qouteall.imm_ptl.core.compat.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobTyped;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.compat.sodium_compatibility.IEChunkJobTyped;

@Mixin(value = ChunkJobTyped.class, remap = false)
public class MixinSodiumChunkJobTyped implements IEChunkJobTyped {
    // The field is declared as `TASK task` on the generic class, which erases to the
    // raw ChunkBuilderTask bound - matching this shadow.
    @Shadow
    @Final
    private ChunkBuilderTask task;
    
    @Override
    public ChunkBuilderTask ip_getTask() {
        return this.task;
    }
}
