package qouteall.imm_ptl.core.compat.mixin.voxy;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.renderer.RendererUsingStencil;

@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.VoxyRenderSystem", remap = false)
public abstract class MixinVoxyRenderSystem {
    @Inject(method = "renderOpaque", at = @At("RETURN"))
    private void iPortals$restorePortalRenderState(CallbackInfo ci) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableDepthTest();
        
        if (IPCGlobal.renderer == IPCGlobal.rendererUsingStencil) {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
            RendererUsingStencil.setStencilLimitation(PortalRendering.getPortalLayer());
        }
    }
}
