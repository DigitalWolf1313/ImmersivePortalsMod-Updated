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
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
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
import net.minecraft.world.phys.HitResult;
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

import java.util.ArrayList;
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
     * The maximum number of portals that one cross-portal interaction can chain through.
     */
    public static final int MAX_PORTAL_LAYERS = 5;
    
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
    
    /**
     * The distance limit gets multiplied by the scale of each portal that a search passes through.
     */
    private record ReachQuery(
        ServerPlayer player,
        ResourceKey<Level> targetDimension,
        Vec3 targetPos,
        double distanceSquare
    ) {
    }
    
    private static boolean canPlayerReach(
        ResourceKey<Level> dimension,
        ServerPlayer player,
        BlockPos requestPos
    ) {
        if (!canDoCrossPortalInteractionEvent.invoker().test(player)) {
            return false;
        }
        
        double playerScale = ScaleUtils.computeBlockReachScale(player);
        double distanceSquare = 6 * 6 * 4 * 4 * playerScale * playerScale;
        
        return findPortalChain(
            player, dimension, Vec3.atCenterOf(requestPos), distanceSquare
        ) != null;
    }
    
    /**
     * Finds the shortest chain of portals that brings the player within reach of the target.
     * Returns an empty list when the target is reachable without any portal.
     * Returns null when the target is not reachable.
     */
    @Nullable
    private static List<Portal> findPortalChain(
        ServerPlayer player,
        ResourceKey<Level> targetDimension,
        Vec3 targetPos,
        double distanceSquare
    ) {
        ReachQuery query = new ReachQuery(player, targetDimension, targetPos, distanceSquare);
        
        for (int depth = 0; depth <= MAX_PORTAL_LAYERS; depth++) {
            List<Portal> chain = searchPortalChain(
                query, player.level(), player.position(), null, 1.0, new ArrayList<>(), depth
            );
            if (chain != null) {
                return chain;
            }
        }
        
        return null;
    }
    
    /**
     * Searches for a chain of exactly {@code remainingDepth} more portals that ends within reach of the target.
     * Returns null when there is no such chain.
     */
    @Nullable
    private static List<Portal> searchPortalChain(
        ReachQuery query,
        Level world,
        Vec3 viewerPos,
        @Nullable Vec3 previousViewerPos,
        double scale,
        List<Portal> chain,
        int remainingDepth
    ) {
        if (remainingDepth == 0) {
            boolean inReach = world.dimension() == query.targetDimension()
                && IPSableIntegration.frameAwareDistanceSqr(world, viewerPos, query.targetPos())
                < query.distanceSquare() * scale * scale;
            return inReach ? new ArrayList<>(chain) : null;
        }
        
        List<Portal> portals = IPMcHelper.getNearbyPortals(
            world, viewerPos, IPGlobal.maxNormalPortalRadius
        ).filter(portal -> portal.isInteractableBy(query.player())).toList();
        
        for (Portal portal : portals) {
            Level destWorld = portal.getDestinationWorld();
            if (destWorld == null) {
                continue;
            }
            
            Vec3 newViewerPos = portal.transformPoint(viewerPos);
            
            // Going back through the portal that was just used only revisits the previous position.
            if (previousViewerPos != null
                && destWorld.dimension() == chain.get(chain.size() - 1).getOriginWorld().dimension()
                && newViewerPos.distanceToSqr(previousViewerPos) < 1.0E-6
            ) {
                continue;
            }
            
            chain.add(portal);
            List<Portal> result = searchPortalChain(
                query, destWorld, newViewerPos, viewerPos,
                scale * portal.getScale(), chain, remainingDepth - 1
            );
            chain.remove(chain.size() - 1);
            
            if (result != null) {
                return result;
            }
        }
        
        return null;
    }
    
    /**
     * Returns the hit result and the dimension to use for placing a block.
     * A block face that touches a global portal makes the placement continue through that portal.
     * The continuation repeats when the destination block face touches another global portal.
     */
    public static Tuple<BlockHitResult, ResourceKey<Level>> getHitResultForPlacing(
        Level world,
        BlockHitResult blockHitResult
    ) {
        Level currentWorld = world;
        BlockPos hitPos = blockHitResult.getBlockPos();
        Direction side = blockHitResult.getDirection();
        boolean passedPortal = false;
        
        for (int layer = 0; layer < MAX_PORTAL_LAYERS; layer++) {
            Portal portal = findPortalAtBlockFace(currentWorld, hitPos, side);
            if (portal == null) {
                break;
            }
            
            Vec3 sideVec = Vec3.atLowerCornerOf(side.getNormal());
            Vec3 newCenter = portal.transformPoint(
                Vec3.atCenterOf(hitPos).add(sideVec.scale(0.501))
            );
            Vec3 newSideVec = portal.transformLocalVecNonScale(sideVec);
            
            currentWorld = portal.getDestinationWorld();
            hitPos = BlockPos.containing(newCenter);
            side = Direction.getNearest(newSideVec.x, newSideVec.y, newSideVec.z);
            passedPortal = true;
        }
        
        if (!passedPortal) {
            return new Tuple<>(blockHitResult, world.dimension());
        }
        
        BlockHitResult newHitResult = new BlockHitResult(
            Vec3.ZERO,
            side.getOpposite(),
            hitPos,
            blockHitResult.isInside()
        );
        
        return new Tuple<>(newHitResult, currentWorld.dimension());
    }
    
    /**
     * Returns the global portal that the face of the block touches, or null if there is none.
     */
    @Nullable
    private static Portal findPortalAtBlockFace(Level world, BlockPos blockPos, Direction side) {
        Vec3 sideVec = Vec3.atLowerCornerOf(side.getNormal());
        Vec3 blockCenter = Vec3.atCenterOf(blockPos);
        
        return GlobalPortalStorage.getGlobalPortals(world).stream().filter(p ->
            p.getNormal().dot(sideVec) < -0.9
                && p.getPortalShape().isBoxInPortalProjection(
                p.getThisSideState(),
                new AABB(blockPos)
            ) && p.getDistanceToPlane(blockCenter) < 0.6
        ).findFirst().orElse(null);
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
        public static void processUseItemPacket(
            ServerPlayer player,
            ResourceKey<Level> dimension,
            byte[] packetBytes
        ) {
            FriendlyByteBuf buf = IPMcHelper.bytesToBuf(packetBytes);
            ServerboundUseItemPacket packet = ServerboundUseItemPacket.STREAM_CODEC.decode(buf);
            
            ServerLevel world = player.server.getLevel(dimension);
            Validate.notNull(world, "missing %s", dimension.location());
            
            withRedirect(
                new Context(world, null),
                () -> doProcessUseItem(world, player, packet)
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
        
        List<Portal> portalChain = findEntityPortalChain(world.dimension(), player, target);
        if (portalChain == null) {
            LOGGER.error("Reject cross-portal entity interact {} {} {}", player, world, target);
            return;
        }
        
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override
            public void onInteraction(InteractionHand hand) {
                if (!target.isAlive()) {
                    return;
                }
                withPlayerRelocatedThroughPortals(
                    player, portalChain, world,
                    () -> player.interactOn(target, hand)
                );
            }
            
            @Override
            public void onInteraction(InteractionHand hand, Vec3 pos) {
                if (target.isRemoved()) {
                    return;
                }
                withPlayerRelocatedThroughPortals(
                    player, portalChain, world,
                    () -> {
                        if (target.interactAt(player, pos, hand).consumesAction()) {
                            player.swing(hand, true);
                        }
                    }
                );
            }
            
            @Override
            public void onAttack() {
                doProcessAttack(world, player, target, portalChain);
            }
        });
    }
    
    /**
     * Cross-portal melee attack. Relocates the player through the portals so knockback
     * and the sword sweep use the target's dimension.
     */
    @IPVanillaCopy
    private static void doProcessAttack(
        ServerLevel world, ServerPlayer player, Entity target, List<Portal> portalChain
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
        
        withPlayerRelocatedThroughPortals(player, portalChain, world, () -> player.attack(target));
    }
    
    /**
     * Runs {@code action} with the player virtually relocated through {@code portals}
     * in order (position, rotation, and world for cross-dimension portals) so vanilla
     * interaction logic uses the target's dimension. Always restores the player.
     */
    private static void withPlayerRelocatedThroughPortals(
        ServerPlayer player, List<Portal> portals, ServerLevel world, Runnable action
    ) {
        if (portals.isEmpty()) {
            action.run();
            return;
        }
        
        Vec3 oldPos = player.position();
        float oldYaw = player.getYRot();
        float oldPitch = player.getXRot();
        Level oldLevel = player.level();
        
        Vec3 newPos = oldPos;
        Vec3 newLook = player.getLookAngle();
        for (Portal portal : portals) {
            newPos = portal.transformPoint(newPos);
            newLook = portal.transformLocalVecNonScale(newLook);
        }
        newLook = newLook.normalize();
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
     * Portal-aware reach check mirroring {@link #canPlayerReach}.
     * Returns the portals to relocate the player through, which is empty when the target is reachable directly.
     * Returns null when the target cannot be reached.
     */
    @Nullable
    private static List<Portal> findEntityPortalChain(
        ResourceKey<Level> dimension,
        ServerPlayer player,
        Entity target
    ) {
        if (!canDoCrossPortalInteractionEvent.invoker().test(player)) {
            return null;
        }
        
        double playerScale = ScaleUtils.computeBlockReachScale(player);
        double reach = player.entityInteractionRange() + 1.0;
        double distanceSquareLimit = reach * reach * playerScale * playerScale;
        
        return findPortalChain(
            player, dimension, target.getBoundingBox().getCenter(), distanceSquareLimit
        );
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
    
    /**
     * {@link ServerGamePacketListenerImpl#handleUseItem(ServerboundUseItemPacket)}
     */
    @IPVanillaCopy
    private static void doProcessUseItem(
        ServerLevel world, ServerPlayer player, ServerboundUseItemPacket packet
    ) {
        player.connection.ackBlockChangesUpTo(packet.getSequence());
        InteractionHand hand = packet.getHand();
        ItemStack itemStack = player.getItemInHand(hand);
        player.resetLastActionTime();
        
        if (itemStack.isEmpty() || !itemStack.isItemEnabled(world.enabledFeatures())) {
            return;
        }
        
        float yRot = Mth.wrapDegrees(packet.getYRot());
        float xRot = Mth.wrapDegrees(packet.getXRot());
        if (xRot != player.getXRot() || yRot != player.getYRot()) {
            player.absRotateTo(yRot, xRot);
        }
        
        PortalUtils.PortalAwareRaytraceResult raytrace = PortalUtils.portalAwareRayTraceFull(
            player.level(),
            player.getEyePosition(),
            player.getViewVector(1),
            Math.max(5.0, player.blockInteractionRange()),
            player,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.ANY,
            new ArrayList<>(),
            MAX_PORTAL_LAYERS
        );
        if (raytrace == null
            || raytrace.world() != world
            || raytrace.hitResult().getType() != HitResult.Type.BLOCK
            || !canPlayerReach(world.dimension(), player, raytrace.hitResult().getBlockPos())
        ) {
            LOGGER.warn("Reject cross-portal item use {} {}", player, world);
            return;
        }
        
        InteractionResult actionResult = player.gameMode.useItem(player, world, itemStack, hand);
        if (actionResult.shouldSwing()) {
            player.swing(hand, true);
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
