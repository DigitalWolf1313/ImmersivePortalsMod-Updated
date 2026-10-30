package qouteall.imm_ptl.core.compat.mixin.voxy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.render.renderer.RendererUsingStencil;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

import static org.lwjgl.opengl.GL11C.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glDisable;

@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.AbstractRenderPipeline", remap = false)
public abstract class MixinVoxyAbstractRenderPipeline {
    @Redirect(
        method = "transformBlitDepth",
        at = @At(
            value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL11C;glDisable(I)V"
        ),
        require = 1
    )
    private static void iPortals$preservePortalRenderState(int capability) {
        if (capability == GL_STENCIL_TEST
            && IPCGlobal.renderer == IPCGlobal.rendererUsingStencil) {
            glEnable(GL_STENCIL_TEST);
            RendererUsingStencil.setStencilLimitation(PortalRendering.getPortalLayer());
            return;
        }
        glDisable(capability);
    }
}
