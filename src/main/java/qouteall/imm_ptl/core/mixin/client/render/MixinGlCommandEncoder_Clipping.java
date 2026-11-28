package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.opengl.GlRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.render.FrontClipping;
import qouteall.imm_ptl.core.render.PortalDraw;

import java.util.Collection;

@Mixin(GlCommandEncoder.class)
public class MixinGlCommandEncoder_Clipping {
    /**
     * Runs after the program of the pass's pipeline is set up and before each draw.
     */
    @Inject(method = "trySetup", at = @At("RETURN"))
    private void ip_afterSetup(
        GlRenderPass renderPass, Collection<String> uniforms, CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValueZ()) {
            FrontClipping.onProgramSetup(((IEGlRenderPass) renderPass).ip_getPipeline().program());
            PortalDraw.afterSetup();
        }
    }
}
