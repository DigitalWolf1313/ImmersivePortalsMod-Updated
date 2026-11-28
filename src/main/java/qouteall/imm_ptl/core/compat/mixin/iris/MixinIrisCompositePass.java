package qouteall.imm_ptl.core.compat.mixin.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.compat.iris_compatibility.ExperimentalIrisPortalRenderer;

@Mixin(targets = "net.irisshaders.iris.pipeline.CompositeRenderer$Pass", remap = false)
public class MixinIrisCompositePass {
    // The framebuffers of the begin, prepare, deferred and composite passes have no depth or stencil attachment.
    // Without one the stencil test never applies, so a pass drawn inside a portal would cover the whole screen.
    // Attaching the main depth-stencil texture keeps each pass inside the stencil area of its portal.
    @WrapOperation(
        method = "setupState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/irisshaders/iris/gl/framebuffer/GlFramebuffer;bind()V"
        )
    )
    private void iPortals$attachStencilBeforeBind(GlFramebuffer framebuffer, Operation<Void> original) {
        if (IPCGlobal.renderer instanceof ExperimentalIrisPortalRenderer
            && !framebuffer.hasDepthAttachment()
        ) {
            framebuffer.addDepthAttachment(Minecraft.getInstance().getMainRenderTarget().getDepthTexture());
        }
        
        original.call(framebuffer);
    }
}
