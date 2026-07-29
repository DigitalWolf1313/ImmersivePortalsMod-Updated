package qouteall.imm_ptl.core.compat.mixin.sable;

import dev.ryanhcode.sable.mixinhelpers.block_outline_render.SubLevelCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Null-guard mixin for {@link SubLevelCamera}.
 * <p>
 * When the camera's {@code renderCamera} field is null (e.g. after {@code clear()} was called
 * but the camera object is still referenced in the render pipeline), all delegation methods
 * return safe defaults instead of throwing a {@link NullPointerException}.
 * <p>
 * Fixes: {@code Cannot invoke "net.minecraft.class_4184.method_19333()" because
 * "this.renderCamera" is null} — triggered by Iris's {@code HandRenderer.canRender} calling
 * {@code SubLevelCamera.isDetached()} after the render camera was cleared.
 */
@Mixin(value = SubLevelCamera.class, remap = false)
public abstract class MixinSableSubLevelCamera {

    @Shadow
    private Camera renderCamera;

    @Inject(method = "setPose", at = @At("HEAD"), cancellable = true)
    private void iPortals$guardSetPose(CallbackInfo ci) {
        if (renderCamera == null) {
            ci.cancel();
        }
    }

    @Inject(method = "getEntity", at = @At("HEAD"), cancellable = true)
    private void iPortals$guardGetEntity(CallbackInfoReturnable<Entity> cir) {
        if (renderCamera == null) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "isInitialized", at = @At("HEAD"), cancellable = true)
    private void iPortals$guardIsInitialized(CallbackInfoReturnable<Boolean> cir) {
        if (renderCamera == null) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
    private void iPortals$guardIsDetached(CallbackInfoReturnable<Boolean> cir) {
        if (renderCamera == null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getNearPlane", at = @At("HEAD"), cancellable = true)
    private void iPortals$guardGetNearPlane(CallbackInfoReturnable<Camera.NearPlane> cir) {
        if (renderCamera == null) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true)
    private void iPortals$guardGetFluidInCamera(CallbackInfoReturnable<FogType> cir) {
        if (renderCamera == null) {
            cir.setReturnValue(FogType.NONE);
        }
    }

    @Inject(method = "reset", at = @At("HEAD"), cancellable = true)
    private void iPortals$guardReset(CallbackInfo ci) {
        if (renderCamera == null) {
            ci.cancel();
        }
    }

    @Inject(method = "getPartialTickTime", at = @At("HEAD"), cancellable = true)
    private void iPortals$guardGetPartialTickTime(CallbackInfoReturnable<Float> cir) {
        if (renderCamera == null) {
            cir.setReturnValue(0.0f);
        }
    }
}