package qouteall.imm_ptl.core.mixin.client.render.framebuffer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ducks.IEFrameBuffer;
import qouteall.imm_ptl.core.render.StencilTargetHelper;


@Mixin(RenderTarget.class)
public abstract class MixinRenderTarget implements IEFrameBuffer {
    
    @Unique
    private boolean isStencilBufferEnabled;
    
    @Shadow
    public int width;
    @Shadow
    public int height;
    
    @Shadow
    @Final
    public boolean useDepth;
    
    @Shadow
    protected GpuTexture depthTexture;
    
    @Shadow
    public abstract void resize(int width, int height);
    
    // The depth texture gets stencil bits only through this reallocation.
    // The framebuffer attachment is handled by MixinDirectStateAccess_Core and MixinDirectStateAccess_Emulated.
    @Inject(method = "createBuffers", at = @At("RETURN"))
    private void onCreatedBuffers(int width, int height, CallbackInfo ci) {
        if (isStencilBufferEnabled && useDepth) {
            StencilTargetHelper.convertToDepthStencil(depthTexture);
        }
    }
    
    @Inject(
        method = "copyDepthFrom",
        at = @At("RETURN")
    )
    private void onCopiedDepthFrom(RenderTarget framebuffer, CallbackInfo ci) {
        CHelper.checkGlError();
    }
    
    @Override
    public boolean ip_getIsStencilBufferEnabled() {
        return isStencilBufferEnabled;
    }
    
    @Override
    public void ip_setIsStencilBufferEnabledAndReload(boolean cond) {
        if (isStencilBufferEnabled != cond) {
            isStencilBufferEnabled = cond;
            resize(width, height);
        }
    }
}
