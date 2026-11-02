package qouteall.imm_ptl.core.compat.mixin.vanillabackport;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.network.PacketRedirection;

/** Routes Platform's entity data to the world containing the tracked entity. */
@Pseudo
@Mixin(targets = "com.blackgear.platform.common.data.entity.SyncedEntityDataContainer", remap = false)
public class MixinVanillaBackportPlatformEntityData {
    @Shadow @Final private Entity entity;
    
    @Group(name = "initialData", min = 1)
    @Redirect(method = "syncTrackingDataTo", at = @At(value = "INVOKE", target = "Lcom/blackgear/platform/core/networking/PayloadDistributor;sendToPlayer(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V", remap = false), remap = false, require = 0)
    private void ip_redirectInitialData(ServerPlayer player, CustomPacketPayload payload) {
        PacketRedirection.withForceRedirect((ServerLevel) entity.level(), () -> ServerPlayNetworking.send(player, payload));
    }
    
    @Group(name = "changedData", min = 1)
    @Redirect(method = "flush", at = @At(value = "INVOKE", target = "Lcom/blackgear/platform/core/networking/PayloadDistributor;sendToPlayersTrackingEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V", remap = false), remap = false, require = 0)
    private void ip_redirectChangedData(Entity tracked, CustomPacketPayload payload) {
        PacketRedirection.withForceRedirect((ServerLevel) tracked.level(), () -> {
            for (ServerPlayer player : PlayerLookup.tracking(tracked)) {
                ServerPlayNetworking.send(player, payload);
            }
        });
    }
    
    @Group(name = "initialData", min = 1)
    @Redirect(method = "syncTrackingDataTo", at = @At(value = "INVOKE", target = "Lcom/blackgear/platform/core/networking/PayloadDistributor;sendToPlayer(Lnet/minecraft/class_3222;Lnet/minecraft/class_8710;)V", remap = false), remap = false, require = 0)
    private void ip_redirectInitialDataProduction(ServerPlayer player, CustomPacketPayload payload) {
        ip_redirectInitialData(player, payload);
    }
    
    @Group(name = "changedData", min = 1)
    @Redirect(method = "flush", at = @At(value = "INVOKE", target = "Lcom/blackgear/platform/core/networking/PayloadDistributor;sendToPlayersTrackingEntity(Lnet/minecraft/class_1297;Lnet/minecraft/class_8710;)V", remap = false), remap = false, require = 0)
    private void ip_redirectChangedDataProduction(Entity tracked, CustomPacketPayload payload) {
        ip_redirectChangedData(tracked, payload);
    }
}
