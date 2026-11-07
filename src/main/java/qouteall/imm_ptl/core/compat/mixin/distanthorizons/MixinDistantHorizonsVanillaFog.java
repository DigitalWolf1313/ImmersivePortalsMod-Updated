package qouteall.imm_ptl.core.compat.mixin.distanthorizons;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.IImmersivePortalsAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Distant Horizons keeps vanilla fog on while a portal renders, because it assumes LODs are not drawn there.
 * LODs are drawn in portal views, so the portal check in the fog decision is forced to false.
 */
@Pseudo
@Mixin(
    targets = "com.seibel.distanthorizons.common.commonMixins.MixinVanillaFogCommon",
    remap = false
)
public abstract class MixinDistantHorizonsVanillaFog {
    @WrapOperation(
        method = "cancelFog",
        at = @At(
            value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/modAccessor/IImmersivePortalsAccessor;isRenderingPortal()Z"
        ),
        remap = false,
        require = 0
    )
    private static boolean ip$ignorePortalRenderingForFog(
        IImmersivePortalsAccessor accessor,
        Operation<Boolean> original
    ) {
        return false;
    }
}
