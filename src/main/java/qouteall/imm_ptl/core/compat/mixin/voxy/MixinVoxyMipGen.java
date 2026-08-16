package qouteall.imm_ptl.core.compat.mixin.voxy;

import it.unimi.dsi.fastutil.bytes.ByteArrayFIFOQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes Voxy's MipGen solidify() thread-safe.
 *
 * Voxy's original code uses shared static SCRATCH/QUEUE buffers, which crash when
 * multiple threads call solidify() concurrently (e.g. when Immersive Portals renders
 * multiple dimensions/worlds).
 */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.model.MipGen")
public class MixinVoxyMipGen {
    // MODEL_TEXTURE_SIZE is checked <= 16 by Voxy's static initializer, so 16x16 is the max.
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