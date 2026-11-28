package qouteall.imm_ptl.core.mixin.client.render.framebuffer;

import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.StencilTargetHelper;

@Mixin(targets = "com.mojang.blaze3d.opengl.DirectStateAccess$Core")
public class MixinDirectStateAccess_Core {
    // Vanilla reuses one framebuffer for clears and copies,
    // so the stencil attachment must be detached when the depth texture has no stencil.
    @Inject(method = "bindFrameBufferTextures", at = @At("TAIL"))
    private void ip_attachStencil(
        int frameBuffer, int colorTexture, int depthTexture, int level, int target, CallbackInfo ci
    ) {
        if (StencilTargetHelper.isDepthStencilTexture(depthTexture)) {
            ARBDirectStateAccess.glNamedFramebufferTexture(
                frameBuffer, GL30.GL_DEPTH_STENCIL_ATTACHMENT, depthTexture, level
            );
        }
        else {
            ARBDirectStateAccess.glNamedFramebufferTexture(
                frameBuffer, GL30.GL_STENCIL_ATTACHMENT, 0, level
            );
        }
    }
}