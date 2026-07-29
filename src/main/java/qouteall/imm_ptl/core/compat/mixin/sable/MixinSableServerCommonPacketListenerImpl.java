package qouteall.imm_ptl.core.compat.mixin.sable;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEPlayerPositionLookS2CPacket;

@Pseudo
@Mixin(ServerCommonPacketListenerImpl.class)
public class MixinSableServerCommonPacketListenerImpl {
    
    @Inject(
        method = "send(Lnet/minecraft/network/protocol/Packet;)V",
        at = @At("HEAD")
    )
    private void iPortals_ensurePlayerPositionPacketHasDimension(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ClientboundPlayerPositionPacket posPacket) {
            // This is needed for Sable or else sitting on a seat on a sublevel will cause a crash
            ResourceKey<Level> dim = ((IEPlayerPositionLookS2CPacket) posPacket).ip_getPlayerDimension();
            if (dim == null) {
                // The target class is ServerCommonPacketListenerImpl, but we need the player from
                // ServerGamePacketListenerImpl. Cast to the more specific type.
                if ((Object) this instanceof ServerGamePacketListenerImpl gameListener) {
                    ServerPlayer player = gameListener.player;
                    if (player != null) {
                        ((IEPlayerPositionLookS2CPacket) posPacket).ip_setPlayerDimension(
                            player.level().dimension()
                        );
                    }
                }
            }
        }
    }
}
