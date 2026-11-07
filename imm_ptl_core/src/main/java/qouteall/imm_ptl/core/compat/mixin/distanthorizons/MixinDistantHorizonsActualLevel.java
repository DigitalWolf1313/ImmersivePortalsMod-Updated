package qouteall.imm_ptl.core.compat.mixin.distanthorizons;

import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

/**
 * Distant Horizons caches the player's level before portal views render.
 * During a portal view on the render thread, this returns the rendered level instead.
 * Other threads keep seeing the player's level,
 * so Distant Horizons' timer thread does not re-center the destination's quad tree at the player's position.
 */
@Pseudo
@Mixin(
    targets = "com.seibel.distanthorizons.common.wrappers.modAccessor.AbstractImmersivePortalsAccessorCommon",
    remap = false
)
public abstract class MixinDistantHorizonsActualLevel {
    @Inject(
        method = "getActualClientLevelWrapper",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void ip$useRenderedLevelDuringPortalPass(
        CallbackInfoReturnable<IClientLevelWrapper> cir
    ) {
        if (!WorldRenderInfo.isRendering() || !Minecraft.getInstance().isSameThread()) {
            return;
        }
        
        ClientLevel renderedLevel = Minecraft.getInstance().level;
        if (renderedLevel == null) {
            return;
        }
        
        IClientLevelWrapper wrapper = ClientLevelWrapper.getWrapper(renderedLevel);
        if (wrapper != null) {
            cir.setReturnValue(wrapper);
        }
    }
}
