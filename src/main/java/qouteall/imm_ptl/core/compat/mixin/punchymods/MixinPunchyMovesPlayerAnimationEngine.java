package qouteall.imm_ptl.core.compat.mixin.punchymods;

import net.minecraft.client.CameraType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@Pseudo
@Mixin(targets = "punchymoves.client.PlayerAnimationEngine", remap = false)
public abstract class MixinPunchyMovesPlayerAnimationEngine {
    // The local player's body is visible through portals even in first person.
    @Redirect(
        method = {"preparePlayer", "itemPose"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z", remap = true),
        require = 0
    )
    private static boolean ip$animatePlayerInPortal(CameraType cameraType) {
        return cameraType.isFirstPerson() && !PortalRendering.isRendering();
    }
}
