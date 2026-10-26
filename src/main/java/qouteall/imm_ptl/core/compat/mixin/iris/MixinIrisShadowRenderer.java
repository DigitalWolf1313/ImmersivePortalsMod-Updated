package qouteall.imm_ptl.core.compat.mixin.iris;

import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisPortalShadowCaster;

@Mixin(value = ShadowRenderer.class, remap = false)
public class MixinIrisShadowRenderer {
    // Inject before the depth copy so both shadow depth textures include portal surfaces.
    @Inject(
        method = "renderShadows",
        at = @At(
            value = "INVOKE",
            target = "Lnet/irisshaders/iris/shadows/ShadowRenderer;copyPreTranslucentDepth(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;)V"
        )
    )
    private void ip_renderPortalShadows(
        LevelRendererAccessor levelRenderer, Camera playerCamera, CallbackInfo ci
    ) {
        IrisPortalShadowCaster.render();
    }
}
