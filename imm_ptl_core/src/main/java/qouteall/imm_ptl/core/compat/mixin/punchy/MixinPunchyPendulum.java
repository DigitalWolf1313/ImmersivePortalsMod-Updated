package qouteall.imm_ptl.core.compat.mixin.punchy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.PunchyTeleportCompat;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Pseudo
@Mixin(targets = "punchy.client.render.PendulumPhysics", remap = false)
public abstract class MixinPunchyPendulum implements PunchyTeleportCompat.Resettable {
    @Shadow private float lastYaw;
    @Shadow private float lastPitch;
    @Shadow private float frameYawDelta;
    @Shadow private float framePitchDelta;
    @Shadow private float framePitch;
    
    @Inject(method = "get", at = @At("RETURN"))
    private static void ip$register(CallbackInfoReturnable<Object> cir) {
        PunchyTeleportCompat.pendulum = (PunchyTeleportCompat.Resettable) cir.getReturnValue();
    }
    
    @Override
    public void ip$resetAfterTeleport() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        
        // Rebase the camera sample without discarding the pendulum's angle or velocity.
        float partialTick = RenderStates.getPartialTick();
        lastYaw = player.getViewYRot(partialTick);
        lastPitch = player.getViewXRot(partialTick);
        framePitch = lastPitch;
        frameYawDelta = 0;
        framePitchDelta = 0;
    }
}
