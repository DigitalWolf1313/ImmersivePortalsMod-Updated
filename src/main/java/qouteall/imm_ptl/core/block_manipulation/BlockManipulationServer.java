package qouteall.imm_ptl.core.block_manipulation;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.compat.sable_compatibility.IPSableIntegration;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalUtils;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;

import java.util.List;
import java.util.function.Predicate;

@SuppressWarnings("resource")
public class BlockManipulationServer {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static record Context(
        ServerLevel world,
        @Nullable BlockHitResult blockHitResult
    ) {
    
    }
    
    /**
     * Whether a player can reach an entity across a portal, plus the portal to
     * virtually relocate through when not reachable directly (null portal = direct).
     */
    private record EntityReachResult(boolean reachable, @Nullable Portal portal) {
    }
    
    public static final ThreadLocal<Context> REDIRECT_CONTEXT =
        ThreadLocal.withInitial(() -> null);
    
    /**
     * Use this event to conditionally disable cross portal block interaction.
     * The result will be ANDed.
     */
    public static final Event<Predicate<Player>> canDoCrossPortalInteractionEvent =
        EventFactory.createArrayBacked(Predicate.class,
            handlers -> player -> {
                for (Predicate<Player> handler : handlers) {
                    if (!handler.test(player)) {
                        return false;
                    }
                }
                return true;
            });
    
    private static boolean canPlayerReach(
        ResourceKey<Level> dimension,
        ServerPlayer player,
        BlockPos requestPos
    ) {
        if (!canDoCrossPortalInteractionEvent.invoker().test(player)) {
            return false;
        }
        
        double playerScale = ScaleUtils.computeBlockReachScale(player);
        
        Vec3 pos = Vec3.atCenterOf(requestPos);
        Vec3 playerPos = player.position();
        double distanceSquare = 6 * 6 * 4 * 4 * playerScale * playerScale;
        if (player.level().dimension() == dimension) {
            if (IPSableIntegration.frameAwareDistanceSqr(player.level(), playerPos, pos)
                < distanceSquare) {
                return true;
            }
        }
        return IPMcHelper.getNearbyPortals(
            player,
            IPGlobal.maxNormalPortalRadius
        ).anyMatch(portal -> {
            if (portal.getDestDim() != dimension || !portal.isInteractableBy(player)) {
                return false;
            }
            net.minecraft.server.level.ServerLevel destLevel =
                player.server.getLevel(portal.getDestDim());
            Vec3 transformed = portal.transformPoint(playerPos);
            double distSq = destLevel != null
                ? IPSableIntegration.frameAwareDistanceSqr(destLevel, transformed, pos)
                : transformed.distanceToSqr(pos);
            return distSq < distanceSquare * portal.getScale() * portal.getScale();
        });
    }
    
    public static Tuple<BlockHitResult, ResourceKey<Level>> getHitResultForPlacing(
        Level world,
        BlockHitResult blockHitResult
    ) {
        Direction side = blockHitResult.getDirection();
        Vec3 sideVec = Vec3.atLowerCornerOf(side.getNormal());
        BlockPos hitPos = blockHitResult.getBlockPos();
        Vec3 hitCenter = Vec3.atCenterOf(hitPos);
        
        List<Portal> globalPortals = GlobalPortalStorage.getGlobalPortals(world);
        
        Portal portal = globalPortals.stream().filter(p ->
            p.getNormal().dot(sideVec) < -0.9
                && p.getPortalShape().isBoxInPortalProjection(
                p.getThisSideState(),
                new AABB(hitPos)
            ) && p.getDistanceToPlane(hitCenter) < 0.6
        ).findFirst().orElse(null);
        
        if (portal == null) {
            return new Tuple<>(blockHitResult, world.dimension());
        }
        
        Vec3 newCenter = portal.transformPoint(hitCenter.add(sideVec.scale(0.501)));
        BlockPos placingBlockPos = BlockPos.containing(newCenter);
        
        BlockHitResult newHitResult = new BlockHitResult(
            Vec3.ZERO,
            side.getOpposite(),
            placingBlockPos,
            blockHitResult.isInside()
        );
        
        return new Tuple<>(newHitResult, portal.getDestDim());
    }
    
    public static class RemoteCallables {
        /**
         * {@link qouteall.imm_ptl.core.mixin.client.interaction.MixinMultiPlayerGameMode#ip_redirectPacket}
         */
        @SuppressWarnings("JavadocReference")
        public static void processPlayerActionPacket(
            ServerPlayer player,
            ResourceKey<Level> dimension,
            byte[] packetBytes
        ) {
            FriendlyByteBuf buf = IPMcHelper.bytesToBuf(packetBytes);
            ServerboundPlayerActionPacket packet = ServerboundPlayerActionPacket.STREAM_CODEC.decode(buf);
            
            ServerLevel world = player.server.getLevel(dimension);
            Validate.notNull(world, "missing %s", dimension.location());
            
            withRedirect(
                new Context(world, null),
                () -> {
                    doProcessPlayerAction(world, player, packet);
                }
            );
        }
        
        /**
         * {@link qouteall.imm_ptl.core.mixin.client.interaction.MixinMultiPlayerGameMode#ip_redirectPacket}
         */
        @SuppressWarnings("JavadocReference")
        public static void processUseItemOnPacket(
            ServerPlayer player,
            ResourceKey<Level> dimension,
            byte[] packetBytes
        ) {
            FriendlyByteBuf buf = IPMcHelper.bytesToBuf(packetBytes);
            ServerboundUseItemOnPacket packet = ServerboundUseItemOnPacket.STREAM_CODEC.decode(buf);
            
            ServerLevel world = player.server.getLevel(dimension);
            Validate.notNull(world, "missing %s", dimension.location());
            
            withRedirect(
                new Context(world, packet.getHitResult()),
                () -> {
                    doProcessUseItemOn(world, player, packet);
                }
            );
        }
        
        /**
         * {@link qouteall.imm_ptl.core.mixin.client.interaction.MixinMultiPlayerGameMode#ip_redirectPacket}
         */
        @SuppressWarnings("JavadocReference")
        public static void processInteractPacket(
            ServerPlayer player,
            ResourceKey<Level> dimension,
            byte[] packetBytes
        ) {
            FriendlyByteBuf buf = IPMcHelper.bytesToBuf(packetBytes);
            ServerboundInteractPacket packet = ServerboundInteractPacket.STREAM_CODEC.decode(buf);
            
            ServerLevel world = player.server.getLevel(dimension);
            Validate.notNull(world, "missing %s", dimension.location());
            
            withRedirect(
                new Context(world, null),
                () -> {
                    doProcessInteract(world, player, packet);
                }
            );
        }
    }
    
    public static void init() {
    
    }
    
    private static void withRedirect(
        Context context,
        Runnable runnable
    ) {
        Context original = REDIRECT_CONTEXT.get();
        REDIRECT_CONTEXT.set(context);
        try {
            PacketRedirection.withForceRedirect(
                context.world(), runnable
            );
        }
        finally {
            REDIRECT_CONTEXT.set(original);
        }
    }
    
    /**
     * {@link ServerGamePacketListenerImpl#handlePlayerAction(ServerboundPlayerActionPacket)}
     */
    @IPVanillaCopy
    private static void doProcessPlayerAction(ServerLevel world, ServerPlayer player, ServerboundPlayerActionPacket packet) {
        player.resetLastActionTime();
        BlockPos blockPos = packet.getPos();
        ServerboundPlayerActionPacket.Action action = packet.getAction();
        
        if (!canPlayerReach(world.dimension(), player, blockPos)) {
            LOGGER.error("Reject cross-portal action {} {} {}", player, world, blockPos);
            return;
        }
        
        if (isAttackingAction(action)) {
            player.gameMode.handleBlockBreakAction(
                blockPos, action, packet.getDirection(),
                world.getMaxBuildHeight(), packet.getSequence()
            );
            player.connection.ackBlockChangesUpTo(packet.getSequence());
        }
    }
    
    public static boolean isAttackingAction(ServerboundPlayerActionPacket.Action action) {
        return action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK ||
            action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK ||
            action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK;
    }
    
    /**
     * {@link ServerGamePacketListenerImpl#handleInteract(ServerboundInteractPacket)}
     */
    @IPVanillaCopy
    private static void doProcessInteract(
        ServerLevel world, ServerPlayer player, ServerboundInteractPacket packet
    ) {
        Entity target = packet.getTarget(world);
        
        player.resetLastActionTime();
        player.setShiftKeyDown(packet.isUsingSecondaryAction());
        
        if (target == null) {
            return;
        }
        
        if (!world.getWorldBorder().isWithinBounds(target.blockPosition())) {
            return;
        }
        
        EntityReachResult reachResult = getEntityReachResult(world.dimension(), player, target);
        if (!reachResult.reachable()) {
            LOGGER.error("Reject cross-portal entity interact {} {} {}", player, world, target);
            return;
        }
        
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override
            public void onInteraction(InteractionHand hand) {
                if (!target.isAlive()) {
                    return;
                }
                withPlayerRelocatedThroughPortal(
                    player, reachResult.portal(), world,
                    () -> player.interactOn(target, hand)
                );
            }
            
            @Override
            public void onInteraction(InteractionHand hand, Vec3 pos) {
                if (target.isRemoved()) {
                    return;
                }
                withPlayerRelocatedThroughPortal(
                    player, reachResult.portal(), world,
                    () -> {
                        if (target.interactAt(player, pos, hand).consumesAction()) {
                            player.swing(hand, true);
                        }
                    }
                );
            }
            
            @Override
            public void onAttack() {
                doProcessAttack(world, player, target, reachResult.portal());
            }
        });
    }
    
    /**
     * Cross-portal melee attack. Relocates the player through the portal so knockback
     * and the sword sweep use the target's dimension.
     */
    @IPVanillaCopy
    private static void doProcessAttack(
        ServerLevel world, ServerPlayer player, Entity target, @Nullable Portal portal
    ) {
        if (target instanceof ItemEntity || target instanceof ExperienceOrb || target == player
            || (target instanceof AbstractArrow arrow && !arrow.isAttackable())
        ) {
            LOGGER.warn(
                "Player {} tried to attack an invalid entity through a portal",
                player.getName().getString()
            );
            return;
        }
        
        ItemStack itemStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!itemStack.isItemEnabled(world.enabledFeatures())) {
            return;
        }
        
        withPlayerRelocatedThroughPortal(player, portal, world, () -> player.attack(target));
    }
    
    /**
     * Runs {@code action} with the player virtually relocated through {@code portal}
     * (position, rotation, and world for cross-dimension portals) so vanilla
     * interaction logic uses the target's dimension. Always restores the player.
     */
    private static void withPlayerRelocatedThroughPortal(
        ServerPlayer player, @Nullable Portal portal, ServerLevel world, Runnable action
    ) {
        if (portal == null) {
            action.run();
            return;
        }
        
        Vec3 oldPos = player.position();
        float oldYaw = player.getYRot();
        float oldPitch = player.getXRot();
        Level oldLevel = player.level();
        
        Vec3 newPos = portal.transformPoint(oldPos);
        Vec3 newLook = portal.transformLocalVecNonScale(player.getLookAngle()).normalize();
        double newYaw = Math.toDegrees(Math.atan2(-newLook.x, newLook.z));
        double newPitch = Math.toDegrees(Math.asin(
            Math.clamp(-newLook.y, -1.0, 1.0)
        ));
        
        player.setPos(newPos);
        player.setYRot((float) newYaw);
        player.setXRot((float) newPitch);
        // cross-dimension: point the player at the target's world so sweep/world queries are correct
        if (oldLevel != world) {
            ((IEEntity) player).ip_setWorld(world);
        }
        try {
            action.run();
        }
        finally {
            if (oldLevel != world) {
                ((IEEntity) player).ip_setWorld(oldLevel);
            }
            player.setPos(oldPos);
            player.setYRot(oldYaw);
            player.setXRot(oldPitch);
        }
    }
    
    /**
     * Portal-aware reach check mirroring {@link #canPlayerReach}. Returns the portal
     * used (null when reachable directly); {@link EntityReachResult#reachable()} is
     * false when the target cannot be reached at all.
     */
    private static EntityReachResult getEntityReachResult(
        ResourceKey<Level> dimension,
        ServerPlayer player,
        Entity target
    ) {
        if (!canDoCrossPortalInteractionEvent.invoker().test(player)) {
            return new EntityReachResult(false, null);
        }
        
        double playerScale = ScaleUtils.computeBlockReachScale(player);
        
        AABB targetBox = target.getBoundingBox();
        Vec3 playerPos = player.position();
        double reach = player.entityInteractionRange() + 1.0;
        double distanceSquareLimit = reach * reach * playerScale * playerScale;
        Portal portal = IPMcHelper.getNearbyPortals(
            player,
            IPGlobal.maxNormalPortalRadius
        ).filter(p -> p.getDestDim() == dimension && p.isInteractableBy(player))
            .filter(p -> {
                ServerLevel destLevel = player.server.getLevel(p.getDestDim());
                Vec3 transformed = p.transformPoint(playerPos);
                double distSq = destLevel != null
                    ? IPSableIntegration.frameAwareDistanceSqr(destLevel, transformed, targetBox.getCenter())
                    : transformed.distanceToSqr(targetBox.getCenter());
                return distSq < distanceSquareLimit * p.getScale() * p.getScale();
            })
            .findFirst()
            .orElse(null);
        
        if (portal != null) {
            return new EntityReachResult(true, portal);
        }
        
        if (player.level().dimension() == dimension
            && IPSableIntegration.frameAwareDistanceSqr(player.level(), playerPos, targetBox.getCenter())
            < distanceSquareLimit) {
            return new EntityReachResult(true, null);
        }
        
        return new EntityReachResult(false, null);
    }
    
    /**
     * {@link ServerGamePacketListenerImpl#handleUseItemOn(ServerboundUseItemOnPacket)}
     */
    @IPVanillaCopy
    private static void doProcessUseItemOn(
        ServerLevel world, ServerPlayer player, ServerboundUseItemOnPacket packet
    ) {
        player.connection.ackBlockChangesUpTo(packet.getSequence());
        InteractionHand hand = packet.getHand();
        BlockHitResult blockHitResult = packet.getHitResult();
        ResourceKey<Level> dimension = world.dimension();
        
        ItemStack itemStack = player.getItemInHand(hand);
        
        if (!itemStack.isItemEnabled(world.enabledFeatures())) {
            return;
        }
        
        BlockPos blockPos = blockHitResult.getBlockPos();
        Direction direction = blockHitResult.getDirection();
        player.resetLastActionTime();
        if (world.mayInteract(player, blockPos)) {
            if (!canPlayerReach(dimension, player, blockPos)) {
                LOGGER.error("Reject cross-portal action {} {} {}", player, world, blockPos);
                return;
            }
            
            InteractionResult actionResult = player.gameMode.useItemOn(
                player,
                world,
                itemStack,
                hand,
                blockHitResult
            );
            if (actionResult.shouldSwing()) {
                player.swing(hand, true);
            }
        }
        
        PacketRedirection.sendRedirectedMessage(
            player,
            dimension,
            new ClientboundBlockUpdatePacket(world, blockPos)
        );
        
        BlockPos offseted = blockPos.relative(direction);
        if (offseted.getY() >= world.getMinBuildHeight() && offseted.getY() < world.getMaxBuildHeight()) {
            PacketRedirection.sendRedirectedMessage(
                player,
                dimension,
                new ClientboundBlockUpdatePacket(world, offseted)
            );
        }
    }
    
    public static boolean validateReach(Player player, Level targetWorld, BlockPos targetPos) {
        PortalUtils.PortalAwareRaytraceResult result = PortalUtils.portalAwareRayTrace(
            player.level(),
            player.getEyePosition(),
            player.getViewVector(1),
            32,
            player,
            ClipContext.Block.COLLIDER
        );
        
        return result != null
            && result.world() == targetWorld
            && result.hitResult().getBlockPos().distManhattan(targetPos) < 8;
    }
    
}
