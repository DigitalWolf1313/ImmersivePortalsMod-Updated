package qouteall.imm_ptl.core.compat.mixin.iris;

import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisTemporalContext;

@Mixin(targets = "net.irisshaders.iris.uniforms.CameraUniforms$CameraPositionTracker", remap = false)
public class MixinIrisCameraPositionTracker {
    @Inject(method = "getCurrentCameraPosition", at = @At("HEAD"), cancellable = true)
    private void ip_current(CallbackInfoReturnable<Vector3d> cir) {
        var view = IrisTemporalContext.current();
        if (view != null) cir.setReturnValue(view.camera);
    }

    @Inject(method = "getPreviousCameraPosition", at = @At("HEAD"), cancellable = true)
    private void ip_previous(CallbackInfoReturnable<Vector3d> cir) {
        var view = IrisTemporalContext.current();
        if (view != null) cir.setReturnValue(view.previousCamera);
    }

    @Inject(method = "getPreviousCameraPositionUnshifted", at = @At("HEAD"), cancellable = true)
    private void ip_previousUnshifted(CallbackInfoReturnable<Vector3d> cir) {
        var view = IrisTemporalContext.current();
        if (view != null) cir.setReturnValue(view.previousUnshiftedCamera);
    }

    @Inject(method = "getCurrentCameraPositionY", at = @At("HEAD"), cancellable = true)
    private void ip_altitude(CallbackInfoReturnable<Double> cir) {
        var view = IrisTemporalContext.current();
        if (view != null) cir.setReturnValue(view.camera.y);
    }
}
