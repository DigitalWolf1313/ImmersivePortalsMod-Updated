package qouteall.imm_ptl.core.compat.mixin.voxy;

import it.unimi.dsi.fastutil.bytes.ByteArrayFIFOQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Gives each thread its own scratch buffers in Voxy's MipGen solidify().
 * Voxy's shared static buffers crash when several threads call it at once,
 * which happens when Immersive Portals renders several dimensions.
 */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.model.MipGen")
public class MixinVoxyMipGen {
    // Voxy limits MODEL_TEXTURE_SIZE to 16, so 16x16 is the largest scratch size needed.
    private static final int SCRATCH_SIZE = 16 * 16;
    
    @Unique
    private static final ThreadLocal<short[]> VOXY_SCRATCH =
        ThreadLocal.withInitial(() -> new short[SCRATCH_SIZE]);
    
    @Unique
    private static final ThreadLocal<ByteArrayFIFOQueue> VOXY_QUEUE =
        ThreadLocal.withInitial(() -> new ByteArrayFIFOQueue(SCRATCH_SIZE));
    
    @Redirect(
        method = "solidify",
        at = @At(
            value = "FIELD",
            target = "Lme/cortex/voxy/client/core/model/MipGen;SCRATCH:[S",
            opcode = org.objectweb.asm.Opcodes.GETSTATIC
        ),
        require = 0
    )
    private static short[] getThreadLocalScratch() {
        return VOXY_SCRATCH.get();
    }
    
    @Redirect(
        method = "solidify",
        at = @At(
            value = "FIELD",
            target = "Lme/cortex/voxy/client/core/model/MipGen;QUEUE:Lit/unimi/dsi/fastutil/bytes/ByteArrayFIFOQueue;",
            opcode = org.objectweb.asm.Opcodes.GETSTATIC
        ),
        require = 0
    )
    private static ByteArrayFIFOQueue getThreadLocalQueue() {
        return VOXY_QUEUE.get();
    }
}