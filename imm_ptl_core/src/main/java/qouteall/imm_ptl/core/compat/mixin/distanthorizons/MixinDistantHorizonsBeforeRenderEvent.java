package qouteall.imm_ptl.core.compat.mixin.distanthorizons;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.IPDistantHorizonsCompat;

/**
 * Distant Horizons' own Immersive Portals listener cancels LOD rendering in portal views.
 * This replaces it so that LODs render in every portal view.
 * The destination level's quad tree is re-centered before Distant Horizons uses it.
 */
@Pseudo
@Mixin(
    targets = "com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.AbstractImmersivePortalsAccessor$BeforeRenderEvent",
    remap = false
)
public abstract class MixinDistantHorizonsBeforeRenderEvent {
    @Inject(method = "beforeRender", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void iPortals$allowRenderingInPortals(CallbackInfo ci) {
        IPDistantHorizonsCompat.tickRenderedLevel();
        ci.cancel();
    }
}
