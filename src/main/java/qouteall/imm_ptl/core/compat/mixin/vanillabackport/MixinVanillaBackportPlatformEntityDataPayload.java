package qouteall.imm_ptl.core.compat.mixin.vanillabackport;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Platform's payload must resolve the entity in the redirected client world. */
@Pseudo
@Mixin(targets = "com.blackgear.platform.core.networking.payloads.ClientboundEntityDataPayload", remap = false)
public class MixinVanillaBackportPlatformEntityDataPayload {
    @Group(name = "world", min = 1)
    @Redirect(method = "lambda$handler$2", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;level()Lnet/minecraft/world/level/Level;", remap = false), remap = false, require = 0)
    private static Level ip_getRedirectedWorld(Player player) {
        return Minecraft.getInstance().level;
    }
    
    @Group(name = "world", min = 1)
    @Redirect(method = "lambda$handler$2", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1657;method_37908()Lnet/minecraft/class_1937;", remap = false), remap = false, require = 0)
    private static Level ip_getRedirectedWorldProduction(Player player) {
        return Minecraft.getInstance().level;
    }
}
