package qouteall.imm_ptl.core.compat.mixin.punchy;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.PunchyTeleportCompat;

@Pseudo
@Mixin(targets = "punchy.client.state.MovementStateMachine", remap = false)
public abstract class MixinPunchyMovement implements PunchyTeleportCompat.Resettable {
    @Shadow private float lastCameraYaw;
    @Shadow private float lastCameraPitch;
    
    @Inject(method = "get", at = @At("RETURN"))
    private static void ip$register(CallbackInfoReturnable<Object> cir) {
        PunchyTeleportCompat.movement = (PunchyTeleportCompat.Resettable) cir.getReturnValue();
    }
    
    @Override
    public void ip$resetAfterTeleport() {
        // Ignore the portal's instantaneous camera rotation on the next tick,
        // but retain the hand's current sway and spring velocity.
        lastCameraYaw = Float.NaN;
        lastCameraPitch = Float.NaN;
    }
}
