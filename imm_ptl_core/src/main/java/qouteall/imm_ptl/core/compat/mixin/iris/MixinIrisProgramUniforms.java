package qouteall.imm_ptl.core.compat.mixin.iris;

import net.irisshaders.iris.gl.program.ProgramUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisTemporalContext;

@Mixin(value = ProgramUniforms.class, remap = false)
public class MixinIrisProgramUniforms {
    @Shadow int lastFrame;
    @Shadow long lastTick;
    @Unique private long ip_uniformEpoch = Long.MIN_VALUE;
    
    @Inject(method = "update", at = @At("HEAD"))
    private void ip_refreshForView(CallbackInfo ci) {
        if (IrisTemporalContext.current() != null && ip_uniformEpoch != IrisTemporalContext.uniformEpoch()) {
            ip_uniformEpoch = IrisTemporalContext.uniformEpoch();
            // Invalidate uploads, not the shader-visible frame counter (used for TAA jitter).
            lastFrame = -1;
            lastTick = -1;
        }
    }
}
