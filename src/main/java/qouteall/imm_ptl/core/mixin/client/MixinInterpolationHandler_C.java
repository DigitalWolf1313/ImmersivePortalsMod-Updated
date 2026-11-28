package qouteall.imm_ptl.core.mixin.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEInterpolationHandler;

@Mixin(InterpolationHandler.class)
public abstract class MixinInterpolationHandler_C implements IEInterpolationHandler {
    @Shadow
    @Final
    private Entity entity;
    
    @Shadow
    private int interpolationSteps;
    
    @Shadow
    public abstract void interpolateTo(Vec3 pos, float yRot, float xRot);
    
    // The vanilla step counter is in a package-private class, so the remaining steps are tracked here.
    @Unique
    private int ip_remainingSteps;
    
    @Override
    public int ip_getRemainingSteps() {
        return ip_remainingSteps;
    }
    
    @Override
    public void ip_interpolateTo(Vec3 pos, float yRot, float xRot, int steps) {
        int oldSteps = interpolationSteps;
        interpolationSteps = steps;
        try {
            interpolateTo(pos, yRot, xRot);
        }
        finally {
            interpolationSteps = oldSteps;
        }
    }
    
    @Inject(method = "interpolateTo", at = @At("RETURN"))
    private void onInterpolateTo(Vec3 pos, float yRot, float xRot, CallbackInfo ci) {
        ip_remainingSteps = interpolationSteps;
        
        // Applies the target position immediately when client entity interpolation is disabled.
        if (!IPGlobal.allowClientEntityPosInterpolation
            && (entity instanceof LivingEntity || entity instanceof AbstractMinecart)
        ) {
            entity.setPos(pos);
        }
    }
    
    @Inject(method = "interpolate", at = @At("TAIL"))
    private void onInterpolate(CallbackInfo ci) {
        if (ip_remainingSteps > 0) {
            ip_remainingSteps--;
        }
    }
    
    @Inject(method = "cancel", at = @At("TAIL"))
    private void onCancel(CallbackInfo ci) {
        ip_remainingSteps = 0;
    }
}
