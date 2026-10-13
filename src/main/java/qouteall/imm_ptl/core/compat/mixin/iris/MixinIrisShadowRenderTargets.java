package qouteall.imm_ptl.core.compat.mixin.iris;

import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.properties.PackShadowDirectives;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.iris_compatibility.IEIrisShadowRenderTargets;
import qouteall.imm_ptl.core.compat.iris_compatibility.ShadowMapSwapper;

@Mixin(value = ShadowRenderTargets.class, remap = false)
public class MixinIrisShadowRenderTargets implements IEIrisShadowRenderTargets {
    ShadowMapSwapper ip_shadowMapSwapper;
    
    @Inject(
        method = "<init>",
        at = @At("RETURN")
    )
    private void onInit(
        WorldRenderingPipeline pipeline,
        int resolution,
        PackShadowDirectives shadowDirectives,
        CallbackInfo ci
    ) {
        ip_shadowMapSwapper = new ShadowMapSwapper(resolution, (ShadowRenderTargets) (Object) this);
    }
    
    @Inject(
        method = "destroy",
        at = @At("HEAD")
    )
    private void onDestroy(CallbackInfo ci) {
        if (ip_shadowMapSwapper != null) {
            ip_shadowMapSwapper.dispose();
            ip_shadowMapSwapper = null;
        }
    }
    
    @Override
    public ShadowMapSwapper getShadowMapSwapper() {
        return ip_shadowMapSwapper;
    }
}
