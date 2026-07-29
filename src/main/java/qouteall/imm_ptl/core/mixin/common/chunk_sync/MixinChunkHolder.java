package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.compat.IPSableCompat;
import qouteall.imm_ptl.core.compat.sable_compatibility.SableInterface;
import qouteall.imm_ptl.core.ducks.IEChunkHolder;
import qouteall.imm_ptl.core.network.PacketRedirection;

import java.util.List;

@Mixin(ChunkHolder.class)
public class MixinChunkHolder implements IEChunkHolder {
    
    @Shadow
    @Final
    private LevelHeightAccessor levelHeightAccessor;
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    @ModifyVariable(
        method = "broadcast",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Packet<?> modifyPacket(Packet<?> packet) {
        ServerLevel serverWorld = (ServerLevel) levelHeightAccessor;
        return PacketRedirection.createRedirectedMessage(
            serverWorld.getServer(),
            serverWorld.dimension(),
            ((Packet) packet)
        );
    }
    
    /**
     * Does not mixin {@link net.minecraft.server.level.ChunkMap#getPlayers(ChunkPos, boolean)}
     * because the current chunk map tracking implementation should coexist with vanilla tracking
     * and avoid deeply interfering with vanilla chunk tracking.
     * 
     * For Sable sublevel chunks, uses Sable's player tracking instead of IP's tracking,
     * as Sable stores sublevel blocks in a far-out plot grid that IP's tracking doesn't cover.
     */
    @Redirect(
        method = "broadcastChanges",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkHolder$PlayerProvider;getPlayers(Lnet/minecraft/world/level/ChunkPos;Z)Ljava/util/List;"
        )
    )
    private List<ServerPlayer> redirectGetPlayers(ChunkHolder.PlayerProvider playerProvider, ChunkPos chunkPos, boolean boundaryOnly) {
        Level level = (Level) levelHeightAccessor;
        
        // Sable plot chunks live in a far grid ImmPtl watch records don't cover
        
        List<ServerPlayer> sablePlayers = null;
        if (IPSableCompat.isSablePresent) {
            sablePlayers = SableInterface.getPlayersTrackingPlotChunk(level, chunkPos);
        }
        if (sablePlayers != null) {
            return sablePlayers;
        }
        
        // Use IP's portal-aware tracking for regular world chunks
        return ImmPtlChunkTracking.getPlayersViewingChunk(
            level.dimension(),
            chunkPos.x, chunkPos.z,
            boundaryOnly
        );
    }
}