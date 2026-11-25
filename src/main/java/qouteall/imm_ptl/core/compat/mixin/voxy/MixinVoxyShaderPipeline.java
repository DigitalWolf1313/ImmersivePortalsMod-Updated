package qouteall.imm_ptl.core.compat.mixin.voxy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.IPVoxyPipelineBridge;

/**
 * Several Voxy pipelines can exist for the same shader data while Immersive Portals renders other dimensions.
 */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.IrisVoxyRenderPipeline", remap = false)
public abstract class MixinVoxyShaderPipeline {
    @Inject(method = "free", at = @At("HEAD"), require = 0)
    private void iPortals$bindBeforeFree(CallbackInfo ci) {
        IPVoxyPipelineBridge.bindPipelineToData(this);
    }
}
