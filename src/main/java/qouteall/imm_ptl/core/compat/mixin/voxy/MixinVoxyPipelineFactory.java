package qouteall.imm_ptl.core.compat.mixin.voxy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.voxy_compatibility.IPVoxyPipelineBridge;

@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.RenderPipelineFactory", remap = false)
public abstract class MixinVoxyPipelineFactory {
    @Inject(method = "createPipeline", at = @At("HEAD"), require = 0)
    private static void iPortals$releaseDataBinding(CallbackInfoReturnable<Object> cir) {
        IPVoxyPipelineBridge.clearCurrentDataBinding();
    }
}
