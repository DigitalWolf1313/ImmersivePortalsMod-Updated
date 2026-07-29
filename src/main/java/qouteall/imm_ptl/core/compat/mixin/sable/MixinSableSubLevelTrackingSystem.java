package qouteall.imm_ptl.core.compat.mixin.sable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.network.udp.SableUDPServer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.network.PacketRedirection;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author r2smith141
 * Cross-dim sub-level tracking mixin.
 *
 * <p>Goal: when a player views a sub-level (airship) in dimension A through a portal from
 * dimension B, they should keep receiving live bounds/movement updates so the sub-level
 * doesn't appear frozen.
 *
 * <p><b>What this mixin does:</b>
 * <ol>
 *   <li>{@code tick HEAD}: query {@link ImmPtlChunkTracking} per sub-level for cross-dim
 *       viewers (players in another dim whose IP chunk-tracking covers the sub-level's
 *       chunk via a portal view). Cache the per-sub-level set and the union.</li>
 *   <li>{@code @WrapOperation} on {@code ServerLevel.getPlayerByUUID} in <b>both</b>
 *       {@code tick} <b>and</b> {@code sendMovementUpdates}: for cross-dim viewers, return
 *       the player from the server-wide list. In {@code tick} this suppresses the removal
 *       path. In {@code sendMovementUpdates} this prevents the silent
 *       {@code if (player == null) continue} that would skip movement-update generation.</li>
 *   <li>{@code @ModifyReturnValue} on {@code shouldLoad}: force {@code true} for cross-dim
 *       viewers, so the removal path's distance check doesn't trigger.</li>
 *   <li>{@code @Inject(HEAD)} on {@code sendBoundsUpdates}: bootstrap path. For cross-dim
 *       viewers detected in step 1 but not yet in {@code tracking}, add them and emit one
 *       {@code sendFullSync} so their client allocates the sub-level. Inert for the common
 *       case (player tracked before crossing -> retained via {@code @WrapOperation} ->
 *       already in tracking when this runs).</li>
 *   <li>{@code @Redirect} on the {@code sendBoundsUpdates} and {@code sendMovementUpdates}
 *       calls in {@code tick}: wrap each in {@code PacketRedirection.withForceRedirect(
 *       sourceLevel, ...)}. Packets emitted under this thread-local get tagged with
 *       source-dim; IP's client-side unwrap dispatches them under
 *       {@code ClientWorldLoader.withSwitchedWorldFailSoft(sourceDim)}, so they land in
 *       the source-dim {@code ClientSubLevelContainer} regardless of which dim the client
 *       is currently displaying.</li>
 *   <li>{@code @WrapOperation} on {@code SableUDPServer.isConnectedTo} in
 *       {@code sendMovementUpdates}: force {@code false} for cross-dim recipients so
 *       movement updates fall through to TCP. Sable's UDP path bypasses
 *       {@code Connection.send} and therefore IP's outgoing-packet wrap; sending UDP to a
 *       cross-dim recipient would result in the packet landing in the wrong client
 *       container. Same-dim recipients keep using UDP.</li>
 * </ol>
 */
@Pseudo
@Mixin(value= SubLevelTrackingSystem.class, remap = false, priority = 900)
public abstract class MixinSableSubLevelTrackingSystem {

    @Shadow @Final
    private ServerLevel level;

    @Shadow
    private void sendFullSync(ServerPlayer player, ServerSubLevel subLevel, CustomPacketPayload extraPacket) {
        throw new AssertionError();
    }

    @Shadow
    private void sendBoundsUpdates(SubLevelContainer container) {
        throw new AssertionError();
    }

    @Shadow
    private void sendMovementUpdates(SubLevelContainer container) {
        throw new AssertionError();
    }

    /** Per-tick: for each sub-level UUID, the set of cross-dim viewer UUIDs. */
    @Unique
    private final Map<UUID, Set<UUID>> iPortals$crossDimViewersBySubLevel = new HashMap<>();

    /** Per-tick union across all sub-levels. Used by hooks that don't have sub-level context. */
    @Unique
    private final Set<UUID> iPortals$crossDimViewerUnion = new HashSet<>();

    // ------------------------------------------------------------------------
    // 1. Compute cross-dim viewer sets at tick HEAD
    // ------------------------------------------------------------------------

    @Inject(method = "tick", at = @At("HEAD"))
    private void iPortals$collectCrossDimViewers(SubLevelContainer container, CallbackInfo ci) {
        iPortals$crossDimViewersBySubLevel.clear();
        iPortals$crossDimViewerUnion.clear();

        for (SubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;

            Vector3dc pos = subLevel.logicalPose().position();
            ChunkPos chunkPos = new ChunkPos(BlockPos.containing(pos.x(), pos.y(), pos.z()));

            List<ServerPlayer> viewers = ImmPtlChunkTracking.getPlayersViewingChunk(
                level.dimension(), chunkPos.x, chunkPos.z, false
            );
            if (viewers.isEmpty()) continue;

            Set<UUID> crossDim = null;
            for (ServerPlayer viewer : viewers) {
                if (viewer.serverLevel() != level) {
                    UUID uuid = viewer.getGameProfile().getId();
                    if (crossDim == null) crossDim = new HashSet<>();
                    crossDim.add(uuid);
                    iPortals$crossDimViewerUnion.add(uuid);
                }
            }
            if (crossDim != null) {
                iPortals$crossDimViewersBySubLevel.put(subLevel.getUniqueId(), crossDim);
            }
        }
    }

    // ------------------------------------------------------------------------
    // 2. Suppress removal for cross-dim viewers in tick()'s null-player check
    // ------------------------------------------------------------------------

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            // NB: bytecode return descriptor is Lnet/minecraft/world/entity/player/Player;
            // (the class lives in the `player` subpackage, not entity directly).
            target = "Lnet/minecraft/server/level/ServerLevel;getPlayerByUUID(Ljava/util/UUID;)Lnet/minecraft/world/entity/player/Player;"
        ),
        require = 1
    )
    private Player iPortals$resolveCrossDimInTick(ServerLevel lvl, UUID uuid, Operation<Player> original) {
        Player p = original.call(lvl, uuid);
        if (p != null) return p;
        if (iPortals$crossDimViewerUnion.contains(uuid)) {
            return lvl.getServer().getPlayerList().getPlayer(uuid);
        }
        return null;
    }

    // ------------------------------------------------------------------------
    // 3. ALSO resolve cross-dim players in sendMovementUpdates -- this was the
    //    missed site that caused the invisibility regression in the prior attempt.
    //    Without this, movement updates iterate `tracking`, hit `getPlayerByUUID
    //    -> null` for cross-dim viewers, and `continue` past them -> no packets.
    // ------------------------------------------------------------------------

    @WrapOperation(
        method = "sendMovementUpdates",
        at = @At(
            value = "INVOKE",
            // Same descriptor fix as above: return type is .../entity/player/Player not .../entity/Player.
            target = "Lnet/minecraft/server/level/ServerLevel;getPlayerByUUID(Ljava/util/UUID;)Lnet/minecraft/world/entity/player/Player;"
        ),
        require = 1
    )
    private Player iPortals$resolveCrossDimInMovement(ServerLevel lvl, UUID uuid, Operation<Player> original) {
        Player p = original.call(lvl, uuid);
        if (p != null) return p;
        if (iPortals$crossDimViewerUnion.contains(uuid)) {
            return lvl.getServer().getPlayerList().getPlayer(uuid);
        }
        return null;
    }

    // ------------------------------------------------------------------------
    // 4. Force shouldLoad=true for cross-dim viewers (their literal world-coord
    //    position is far from the source-dim sub-level, so vanilla distance check
    //    would say "out of range" and trigger the removal path).
    // ------------------------------------------------------------------------

    @ModifyReturnValue(
        method = "shouldLoad",
        at = @At("RETURN"),
        remap = false
    )
    private boolean iPortals$forceShouldLoadForCrossDim(boolean original, Player player, Vector3dc entityPosition) {
        if (original) return true;
        if (player instanceof ServerPlayer sp && sp.serverLevel() != level) {
            if (iPortals$crossDimViewerUnion.contains(sp.getGameProfile().getId())) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------
    // 5. Bootstrap: for any cross-dim viewer NOT yet in tracking (e.g. someone
    //    who walked up to a portal in the dest dim without having tracked the
    //    source-dim sub-level before), add them to tracking + emit a wrapped
    //    sendFullSync. Inert when the viewer was already tracking before
    //    crossing (which is the common case -- they're retained via @WrapOperation
    //    above, so they're already in tracking when this runs).
    //
    //    Anchored at HEAD of sendBoundsUpdates rather than at the invoke site in
    //    tick(), to avoid colliding with the @Redirect on the same instruction.
    //    sendBoundsUpdates is the first packet-emitting call in tick after the
    //    addition/removal passes, so HEAD here is the right bootstrap window.
    // ------------------------------------------------------------------------

    @Inject(method = "sendBoundsUpdates", at = @At("HEAD"), require = 0)
    private void iPortals$bootstrapCrossDimViewers(SubLevelContainer container, CallbackInfo ci) {
        if (iPortals$crossDimViewersBySubLevel.isEmpty()) return;
        for (SubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.isRemoved()) continue;
            ServerSubLevel serverSubLevel = (ServerSubLevel) subLevel;
            Set<UUID> crossDim = iPortals$crossDimViewersBySubLevel.get(serverSubLevel.getUniqueId());
            if (crossDim == null || crossDim.isEmpty()) continue;

            Collection<UUID> tracking = serverSubLevel.getTrackingPlayers();
            for (UUID uuid : crossDim) {
                if (tracking.contains(uuid)) continue;
                ServerPlayer viewer = level.getServer().getPlayerList().getPlayer(uuid);
                if (viewer == null) continue;
                tracking.add(uuid);
                // We're already inside the @Redirect's withForceRedirect wrap when
                // sendBoundsUpdates is being called from tick. But we may also be called
                // outside that context (e.g. if sendBoundsUpdates is invoked elsewhere),
                // so set it explicitly here -- nested same-value is a no-op.
                PacketRedirection.withForceRedirect(level, () -> sendFullSync(viewer, serverSubLevel, null));
            }
        }
    }

    // ------------------------------------------------------------------------
    // 6. Wrap sendBoundsUpdates + sendMovementUpdates calls in tick() in
    //    PacketRedirection.withForceRedirect. This tags emitted packets with the
    //    source dim's ResourceKey; IP's client-side unwrap dispatches them under
    //    ClientWorldLoader.withSwitchedWorldFailSoft so they land in the
    //    source-dim ClientSubLevelContainer regardless of the client's active dim.
    // ------------------------------------------------------------------------

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/system/SubLevelTrackingSystem;sendBoundsUpdates(Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;)V"
        ),
        require = 0
    )
    private void iPortals$wrapBoundsUpdatesInRedirect(SubLevelTrackingSystem self, SubLevelContainer container) {
        PacketRedirection.withForceRedirect(level, () -> sendBoundsUpdates(container));
    }

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/system/SubLevelTrackingSystem;sendMovementUpdates(Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;)V"
        ),
        require = 0
    )
    private void iPortals$wrapMovementUpdatesInRedirect(SubLevelTrackingSystem self, SubLevelContainer container) {
        PacketRedirection.withForceRedirect(level, () -> sendMovementUpdates(container));
    }

    // ------------------------------------------------------------------------
    // 7. Force TCP for cross-dim recipients in sendMovementUpdates. Sable's UDP
    //    fast-path bypasses Connection.send and therefore IP's outgoing-packet
    //    wrap; the client would receive an unwrapped packet and route it to its
    //    currently-active container (wrong dim). TCP fallback goes through
    //    Connection.send and gets wrapped by IP under the active
    //    withForceRedirect from @Redirect above.
    // ------------------------------------------------------------------------

    @WrapOperation(
        method = "sendMovementUpdates",
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/network/udp/SableUDPServer;isConnectedTo(Lnet/minecraft/server/level/ServerPlayer;)Z"
        ),
        require = 1
    )
    private boolean iPortals$forceCrossDimToTCP(SableUDPServer server, ServerPlayer player, Operation<Boolean> original) {
        if (player.serverLevel() != level) {
            return false;
        }
        return original.call(server, player);
    }

    // ------------------------------------------------------------------------
    // 8. Stamp StopTracking packets with the source dim. sendRemoval
    //    fires from tick's removal pass and from onSubLevelRemoved -- both outside the
    //    bounds/movement redirect wraps -- and its packets must land in the client's
    //    sublevels-dim container, not whatever dim the recipient currently displays.
    // ------------------------------------------------------------------------

    @WrapOperation(
        method = {"tick", "onSubLevelRemoved"},
        at = @At(
            value = "INVOKE",
            target = "Ldev/ryanhcode/sable/sublevel/system/SubLevelTrackingSystem;sendRemoval(Lfoundry/veil/api/network/VeilPacketManager$PacketSink;Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V"
        ),
        require = 1
    )
    private void iPortals$stampRemovalWithSourceDim(
        SubLevelTrackingSystem self,
        foundry.veil.api.network.VeilPacketManager.PacketSink sink,
        ServerSubLevel subLevel,
        Operation<Void> original
    ) {
        PacketRedirection.withForceRedirect(level, () -> original.call(self, sink, subLevel));
    }
}