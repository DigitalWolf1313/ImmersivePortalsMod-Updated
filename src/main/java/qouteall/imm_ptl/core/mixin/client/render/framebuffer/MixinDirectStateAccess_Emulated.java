package qouteall.imm_ptl.core.mixin.client.render.framebuffer;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.StencilTargetHelper;

@Mixin(targets = "com.mojang.blaze3d.opengl.DirectStateAccess$Emulated")
public class MixinDirectStateAccess_Emulated {
    // Vanilla reuses one framebuffer for clears and copies,
    // so the stencil attachment must be detached when the depth texture has no stencil.
    @Inject(method = "bindFrameBufferTextures", at = @At("TAIL"))
    private void ip_attachStencil(
        int frameBuffer, int colorTexture, int depthTexture, int level, int target, CallbackInfo ci
    ) {
        int bindTarget = target == 0 ? GL30.GL_DRAW_FRAMEBUFFER : target;
        int oldFrameBuffer = GlStateManager.getFrameBuffer(bindTarget);
        
        GlStateManager._glBindFramebuffer(bindTarget, frameBuffer);
        
        if (StencilTargetHelper.isDepthStencilTexture(depthTexture)) {
            GlStateManager._glFramebufferTexture2D(
                bindTarget, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_TEXTURE_2D, depthTexture, level
            );
        }
        else {
            GlStateManager._glFramebufferTexture2D(
                bindTarget, GL30.GL_STENCIL_ATTACHMENT, GL30.GL_TEXTURE_2D, 0, level
            );
        }
        
        if (target == 0) {
            GlStateManager._glBindFramebuffer(bindTarget, oldFrameBuffer);
        }
    }
}