package qouteall.imm_ptl.core.compat.mixin.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.compat.iris_compatibility.ExperimentalIrisPortalRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

import java.nio.IntBuffer;

@Mixin(value = CompositeRenderer.class, remap = false)
public class MixinIrisCompositeRenderer {
    // The framebuffers of the begin, prepare, deferred and composite passes have no depth or stencil attachment.
    // Without one the stencil test never applies, so a pass drawn inside a portal would cover the whole screen.
    // Attaching the main depth-stencil texture keeps each pass inside the stencil area of its portal.
    @WrapOperation(
        method = "renderAll",
        at = @At(
            value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/framebuffer/GlFramebuffer;bind()V"
        )
    )
    private void iPortals$attachStencilBeforeBind(GlFramebuffer framebuffer, Operation<Void> original) {
        if (IPCGlobal.renderer instanceof ExperimentalIrisPortalRenderer
            && !framebuffer.hasDepthAttachment()
        ) {
            framebuffer.addDepthAttachment(Minecraft.getInstance().getMainRenderTarget().getDepthTextureId());
        }
        
        original.call(framebuffer);
    }
    
    @WrapOperation(
        method = "renderAll",
        at = @At(
            value = "INVOKE",
            target = "Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"
        )
    )
    private void iPortals$skipScaledPortalCompositePass(
        FullScreenQuadRenderer renderer, Operation<Void> original
    ) {
        if (IPCGlobal.renderer instanceof ExperimentalIrisPortalRenderer
            && PortalRendering.isRendering()) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer viewport = stack.mallocInt(4);
                GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
                var mainTarget = Minecraft.getInstance().getMainRenderTarget();
                if (viewport.get(2) != mainTarget.width || viewport.get(3) != mainTarget.height) {
                    return;
                }
            }
        }
        original.call(renderer);
    }
}
