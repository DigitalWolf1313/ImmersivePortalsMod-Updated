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
 * Distant Horizons keys its lightmaps by the level from {@code getLightmapClientLevelWrapper()},
 * which is not updated while a portal view renders.
 * Using the rendered level keeps destination LODs from getting the source dimension's lightmap.
 */
@Pseudo
@Mixin(
    targets = "com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper",
    remap = false
)
public abstract class MixinDistantHorizonsLightmapLevel {
    @Inject(
        method = "getLightmapClientLevelWrapper",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void ip$usePortalDestinationLevelForLightmap(
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
