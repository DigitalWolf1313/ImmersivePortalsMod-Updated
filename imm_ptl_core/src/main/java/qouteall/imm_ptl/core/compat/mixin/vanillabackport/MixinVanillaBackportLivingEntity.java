package qouteall.imm_ptl.core.compat.mixin.vanillabackport;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.IPVanillaBackportCompat;

/**
 * Rotates Vanilla Backport spear knockback for targets hit through portals.
 */
@Mixin(LivingEntity.class)
public class MixinVanillaBackportLivingEntity {
    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void iPortals$fixSpearKnockbackThroughPortal(
        double strength, double x, double z, CallbackInfo ci
    ) {
        double[] fixed = IPVanillaBackportCompat.getFixedKnockbackDirection(
            (LivingEntity) (Object) this, x, z
        );
        if (fixed == null) {
            return;
        }
        
        ci.cancel();
        
        IPVanillaBackportCompat.setApplyingKnockbackFix(true);
        try {
            ((LivingEntity) (Object) this).knockback(strength, fixed[0], fixed[1]);
        }
        finally {
            IPVanillaBackportCompat.setApplyingKnockbackFix(false);
        }
    }
}
