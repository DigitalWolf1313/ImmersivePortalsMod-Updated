package qouteall.imm_ptl.core.compat.mixin.vanillabackport;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Group;
import qouteall.imm_ptl.core.network.PacketRedirection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Pseudo
@Mixin(targets = "com.blackgear.platform.common.data.entity.SyncedEntityDataContainer", remap = false)
public class MixinVanillaBackportPlatformEntityData {
    @Shadow
    @Final
    private Entity entity;
    
    @Group(name = "initialData", min = 1)
    @WrapOperation(
        method = "syncTrackingDataTo",
        at = @At(
            value = "INVOKE",
            target = "Lcom/blackgear/platform/core/network/NetworkChannel;sendToPlayer(Lcom/blackgear/platform/core/network/base/Packet;Lnet/minecraft/world/entity/player/Player;)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void ip_redirectInitialData(
        @Coerce Object networkChannel, @Coerce Object packet, Player player, Operation<Void> original
    ) {
        PacketRedirection.withForceRedirect((ServerLevel) entity.level(), () ->
            original.call(networkChannel, packet, player)
        );
    }
    
    @Group(name = "initialData", min = 1)
    @WrapOperation(
        method = "syncTrackingDataTo",
        at = @At(
            value = "INVOKE",
            target = "Lcom/blackgear/platform/core/network/NetworkChannel;sendToPlayer(Lcom/blackgear/platform/core/network/base/Packet;Lnet/minecraft/class_1657;)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void ip_redirectInitialDataProduction(
        @Coerce Object networkChannel, @Coerce Object packet, Player player, Operation<Void> original
    ) {
        ip_redirectInitialData(networkChannel, packet, player, original);
    }
    
    @Group(name = "changedData", min = 1)
    @WrapOperation(
        method = "flush",
        at = @At(
            value = "INVOKE",
            target = "Lcom/blackgear/platform/core/network/NetworkChannel;sendToPlayersTrackingEntity(Lcom/blackgear/platform/core/network/base/Packet;Lnet/minecraft/world/entity/Entity;)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void ip_redirectChangedData(
        @Coerce Object networkChannel, @Coerce Object packet, Entity tracked, Operation<Void> original
    ) {
        PacketRedirection.withForceRedirect((ServerLevel) tracked.level(), () ->
            original.call(networkChannel, packet, tracked)
        );
    }
    
    @Group(name = "changedData", min = 1)
    @WrapOperation(
        method = "flush",
        at = @At(
            value = "INVOKE",
            target = "Lcom/blackgear/platform/core/network/NetworkChannel;sendToPlayersTrackingEntity(Lcom/blackgear/platform/core/network/base/Packet;Lnet/minecraft/class_1297;)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void ip_redirectChangedDataProduction(
        @Coerce Object networkChannel, @Coerce Object packet, Entity tracked, Operation<Void> original
    ) {
        ip_redirectChangedData(networkChannel, packet, tracked, original);
    }
}
