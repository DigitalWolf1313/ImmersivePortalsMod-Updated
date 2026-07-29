package qouteall.imm_ptl.core.compat.mixin.veil;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.ClientWorldLoader;

@Pseudo
@Mixin(targets = "foundry.veil.api.network.handler.PacketContext")
@Environment(EnvType.CLIENT)
public interface MixinVeilPacketContext {

    @ModifyReturnValue(
        method = "level",
        at = @At("RETURN"),
        require = 1
    )
    default Level iPortals$redirectLevel(Level original) {
        if (!ClientWorldLoader.getIsWorldSwitched()) {
            return original;
        }

        Level mcLevel = Minecraft.getInstance().level;
        if (mcLevel == null || mcLevel == original) {
            return original;
        }

        return mcLevel;
    }
}