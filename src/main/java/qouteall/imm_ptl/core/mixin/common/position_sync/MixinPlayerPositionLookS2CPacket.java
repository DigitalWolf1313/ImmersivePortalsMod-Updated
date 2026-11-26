package qouteall.imm_ptl.core.mixin.common.position_sync;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEPlayerPositionLookS2CPacket;

@Mixin(ClientboundPlayerPositionPacket.class)
public class MixinPlayerPositionLookS2CPacket implements IEPlayerPositionLookS2CPacket {
    @Shadow
    @Final
    @Mutable
    private static StreamCodec<FriendlyByteBuf, ClientboundPlayerPositionPacket> STREAM_CODEC;

    private ResourceKey<Level> playerDimension;
    
    @Override
    public ResourceKey<Level> ip_getPlayerDimension() {
        return playerDimension;
    }
    
    @Override
    public void ip_setPlayerDimension(ResourceKey<Level> dimension) {
        playerDimension = dimension;
    }
    
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void extendStreamCodec(CallbackInfo ci) {
        StreamCodec<FriendlyByteBuf, ClientboundPlayerPositionPacket> vanillaCodec = STREAM_CODEC;
        STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                vanillaCodec.encode(buf, packet);
                ResourceKey<Level> dimension =
                    ((IEPlayerPositionLookS2CPacket) (Object) packet).ip_getPlayerDimension();
                if (dimension == null) {
                    throw new IllegalStateException("Player position packet dimension is not set");
                }
                buf.writeResourceKey(dimension);
            },
            vanillaCodec::decode
        );
    }
}
