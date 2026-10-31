package qouteall.imm_ptl.core.compat.mixin.vista;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@Pseudo
@Mixin(targets = "net.mehvahdjukaar.vista.client.ViewFinderController", remap = false)
public abstract class MixinVistaCameraController {
    @Inject(method = "setupCamera", at = @At("HEAD"), cancellable = true)
    private static void ip$skipPortalCamera(Camera camera, BlockGetter level, Entity entity,
        boolean detached, boolean reverse, float partialTick, CallbackInfoReturnable<Boolean> cir) {
        if (WorldRenderInfo.isRendering()) {
            cir.setReturnValue(false);
        }
    }
}
