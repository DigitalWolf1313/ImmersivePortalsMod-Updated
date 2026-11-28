package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.render.IPRenderSystem;

@Mixin(PerspectiveProjectionMatrixBuffer.class)
public class MixinPerspectiveProjectionMatrixBuffer {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void ip_recordMatrix(Matrix4f pose, CallbackInfoReturnable<GpuBufferSlice> cir) {
        IPRenderSystem.recordProjectionMatrix(cir.getReturnValue(), pose);
    }
}
