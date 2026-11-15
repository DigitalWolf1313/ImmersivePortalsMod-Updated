package qouteall.imm_ptl.peripheral.mixin.client.portal_wand;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.peripheral.wand.ClientPortalWandPortalAnimate;

@Mixin(MouseHandler.class)
public class MixinMouseHandler_PortalWand {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onOnScroll(long windowPointer, double xOffset, double yOffset, CallbackInfo ci) {
        if (ClientPortalWandPortalAnimate.onScroll(yOffset)) {
            ci.cancel();
        }
    }
}
