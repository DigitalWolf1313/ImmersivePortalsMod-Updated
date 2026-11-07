package qouteall.imm_ptl.core.compat.mixin.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.pipeline.PipelineManager;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisTemporalContext;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@Mixin(value = PipelineManager.class, remap = false)
public class MixinIrisPipelineManager {
    @WrapOperation(method = "preparePipeline", at = @At(value = "INVOKE",
        target = "Lnet/irisshaders/iris/uniforms/SystemTimeUniforms$FrameCounter;reset()V"))
    private void ip_preserveFrame(SystemTimeUniforms.FrameCounter counter, Operation<Void> original) {
        if (!IrisTemporalContext.isEnabled() || !WorldRenderInfo.isRendering()) original.call(counter);
    }

    @WrapOperation(method = "preparePipeline", at = @At(value = "INVOKE",
        target = "Lnet/irisshaders/iris/uniforms/SystemTimeUniforms$Timer;reset()V"))
    private void ip_preserveTime(SystemTimeUniforms.Timer timer, Operation<Void> original) {
        if (!IrisTemporalContext.isEnabled() || !WorldRenderInfo.isRendering()) original.call(timer);
    }
}
