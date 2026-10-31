package qouteall.imm_ptl.core.compat.mixin.emf;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@Pseudo
@Environment(EnvType.CLIENT)
@Mixin(targets = "traben.entity_model_features.utils.EMFLODHandler", remap = false)
public abstract class MixinEntityModelFeaturesLODHandler {
    @Inject(method = "isLODSkippingThisFrame", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ip$animateCrossPortalEntity(String modelId, CallbackInfoReturnable<Boolean> cir) {
        if (CrossPortalEntityRenderer.isRenderingEntityProjection ||
            CrossPortalEntityRenderer.isRenderingPortalIntersectingEntity) {
            cir.setReturnValue(false);
        }
    }
    
    @ModifyVariable(
        method = "distanceOfEntityFrom",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0
    )
    private static BlockPos ip$portalCameraForAnimationLod(BlockPos playerPos) {
        return PortalRendering.isRendering()
            ? BlockPos.containing(PortalRendering.getRenderingCameraPos())
            : playerPos;
    }
}
