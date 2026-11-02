package qouteall.imm_ptl.core.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;

@Mixin(LivingEntity.class)
public class MixinLivingEntity_C {
    // Applies the target position immediately when client entity interpolation is disabled.
    @Inject(
        method = "lerpTo",
        at = @At("RETURN")
    )
    private void onUpdateTrackedPositionAndAngles(
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        int interpolationSteps,
        CallbackInfo ci
    ) {
        LivingEntity this_ = ((LivingEntity) (Object) this);
        if (!IPGlobal.allowClientEntityPosInterpolation) {
            this_.setPos(x, y, z);
        }
    }
}
