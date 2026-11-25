package qouteall.imm_ptl.core.compat.mixin.voxy;

import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.IPVoxyIrisSamplers;

@Pseudo
@Mixin(targets = "me.cortex.voxy.client.iris.VoxySamplers", remap = false)
public abstract class MixinVoxySamplers {
    @Inject(method = "addSamplers", at = @At("HEAD"), cancellable = true, require = 0)
    private static void iPortals$addSamplers(
        IrisRenderingPipeline pipeline, SamplerHolder samplers, CallbackInfo ci
    ) {
        IPVoxyIrisSamplers.addSamplers(pipeline, samplers);
        ci.cancel();
    }
}
