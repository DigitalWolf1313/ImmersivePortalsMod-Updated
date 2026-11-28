package qouteall.imm_ptl.core.teleportation;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEInterpolationHandler;
import qouteall.imm_ptl.core.mixin.client.accessor.IELivingEntityInterpolation;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.DQuaternion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Client-side handling of non-player entities that the server teleports through a portal.
 * The interpolated client entity can still be short of the portal when the teleport arrives.
 * The handoff is delayed until the entity reaches the portal, so that it does not visibly jump.
 */
@Environment(EnvType.CLIENT)
public class ClientEntityPortalHandoff {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final int MAX_HANDOFF_TICKS = 6;
    private static final int INTERPOLATION_STEPS = 3;
    
    // The velocity limit is in blocks per tick.
    private static final double MAX_HANDOFF_SPEED = 15;
    
    // The mapped client position may differ from the server position by this many blocks plus a share of the speed.
    // A larger error can mean that the entity was just tracked.
    private static final double ERROR_ALLOWANCE_BASE = 8;
    private static final double ERROR_ALLOWANCE_PER_SPEED = 20;
    
    // An interpolation target closer than this to the entity counts as already reached.
    static final double LERP_TARGET_EPSILON_SQR = 1.0E-12;
    
    private static final Map<Integer, PendingEntityTeleport> pendingEntityTeleports = new HashMap<>();
    private static final Map<Integer, PendingCrossDimensionEntityTeleport> pendingCrossDimensionEntityTeleports = new HashMap<>();
    
    private interface PendingHandoff {
        /**
         * Advances the wait by one tick and returns whether the handoff should finish now.
         */
        boolean shouldFinish();
        
        void finish();
    }
    
    private static class PendingCrossDimensionEntityTeleport implements PendingHandoff {
        final Entity sourceEntity;
        final Portal portal;
        final ResourceKey<Level> destination;
        int ticks;
        boolean removalReceived;
        
        PendingCrossDimensionEntityTeleport(Entity sourceEntity, Portal portal, ResourceKey<Level> destination) {
            this.sourceEntity = sourceEntity;
            this.portal = portal;
            this.destination = destination;
        }
        
        /**
         * Returns the destination entity that corresponds to the source entity.
         * Returns null when the destination world has not received it yet.
         */
        @Nullable
        Entity findDestinationEntity() {
            ClientLevel destinationWorld = ClientWorldLoader.getOptionalWorld(destination);
            if (destinationWorld == null) return null;
            
            Entity entity = destinationWorld.getEntity(sourceEntity.getId());
            return entity != null && entity.getUUID().equals(sourceEntity.getUUID()) ? entity : null;
        }
        
        @Override
        public boolean shouldFinish() {
            ++ticks;
            boolean destinationReady = findDestinationEntity() != null;
            boolean crossed = hasCrossedPlane(portal, sourceEntity);
            
            // The transferred render history already covers the crossing frame.
            // Waiting another tick would let the source reach its lerp target and visibly stop.
            return sourceEntity.isRemoved() || portal.isRemoved() ||
                (crossed && destinationReady) || ticks >= MAX_HANDOFF_TICKS;
        }
        
        @Override
        public void finish() {
            pendingCrossDimensionEntityTeleports.remove(sourceEntity.getId(), this);
            Entity destinationEntity = findDestinationEntity();
            if (!sourceEntity.isRemoved() && !portal.isRemoved() && destinationEntity != null) {
                ClientEntityRenderStateTransfer.transfer(sourceEntity, destinationEntity, portal);
            }
            ClientLevel sourceWorld = (ClientLevel) sourceEntity.level();
            if (removalReceived && sourceWorld.getEntity(sourceEntity.getId()) == sourceEntity) {
                sourceWorld.removeEntity(sourceEntity.getId(), Entity.RemovalReason.CHANGED_DIMENSION);
            }
        }
    }
    
    private static class PendingEntityTeleport implements PendingHandoff {
        final Entity entity;
        final Portal portal;
        final Runnable teleport;
        final List<Packet<ClientGamePacketListener>> packets = new ArrayList<>();
        int ticks;
        
        PendingEntityTeleport(Entity entity, Portal portal, Runnable teleport) {
            this.entity = entity;
            this.portal = portal;
            this.teleport = teleport;
        }
        
        @Override
        public boolean shouldFinish() {
            return entity.isRemoved() || portal.isRemoved() ||
                hasCrossedPlane(portal, entity) || ++ticks >= MAX_HANDOFF_TICKS;
        }
        
        @Override
        public void finish() {
            pendingEntityTeleports.remove(entity.getId(), this);
            if (entity.isRemoved() || entity.level().getEntity(entity.getId()) != entity) return;
            LOGGER.debug("Finishing entity portal handoff: id={} ticks={} distance={} queuedPackets={}",
                entity.getId(), ticks, portal.getDistanceToPlane(entity.getEyePosition()), packets.size());
            teleport.run();
            ClientPacketListener listener = ClientTeleportationManager.client.getConnection();
            if (listener != null) {
                ClientWorldLoader.withSwitchedWorldFailSoft(entity.level().dimension(), () -> {
                    for (Packet<ClientGamePacketListener> packet : packets) packet.handle(listener);
                });
            }
        }
    }
    
    /**
     * The state of a same-dimension teleport as the server sent it.
     * The portal transform is read once when the teleport arrives,
     * so a delayed handoff is not affected by later portal animation.
     */
    private record ServerHandoff(
        Vec3 pos, float yaw, float pitch, float headYaw, float oldYaw, float oldPitch, double speed,
        Function<Vec3, Vec3> transformPoint, Function<Vec3, Vec3> transformVec
    ) {}
    
    public static void tick() {
        tickPending(pendingCrossDimensionEntityTeleports);
        tickPending(pendingEntityTeleports);
    }
    
    private static void tickPending(Map<Integer, ? extends PendingHandoff> pendingMap) {
        if (pendingMap.isEmpty()) return;
        
        for (PendingHandoff pending : new ArrayList<PendingHandoff>(pendingMap.values())) {
            if (pending.shouldFinish()) {
                pending.finish();
            }
        }
    }
    
    public static void clear() {
        pendingEntityTeleports.clear();
        pendingCrossDimensionEntityTeleports.clear();
    }
    
    public static boolean preserveSourceEntity(ClientLevel world, int entityId) {
        PendingCrossDimensionEntityTeleport pending = pendingCrossDimensionEntityTeleports.get(entityId);
        return pending != null && pending.sourceEntity.level() == world &&
            world.getEntity(entityId) == pending.sourceEntity;
    }
    
    public static boolean deferSourceEntityRemoval(ClientLevel world, int entityId) {
        if (!preserveSourceEntity(world, entityId)) return false;
        pendingCrossDimensionEntityTeleports.get(entityId).removalReceived = true;
        return true;
    }
    
    public static boolean hideDestinationEntity(Entity entity) {
        PendingCrossDimensionEntityTeleport pending = pendingCrossDimensionEntityTeleports.get(entity.getId());
        return pending != null && entity != pending.sourceEntity && entity.level().dimension().equals(pending.destination);
    }
    
    public static boolean deferEntityMovementPacket(Entity entity, Packet<ClientGamePacketListener> packet) {
        PendingEntityTeleport pending = pendingEntityTeleports.get(entity.getId());
        if (pending == null) return false;
        pending.packets.add(packet);
        return true;
    }
    
    public static void setInterpolationTarget(Entity entity, Vec3 target, float yaw, float pitch, int steps) {
        InterpolationHandler interpolation = entity.getInterpolation();
        if (interpolation != null) {
            ((IEInterpolationHandler) interpolation).ip_interpolateTo(target, yaw, pitch, steps);
        }
    }
    
    static float getLerpTargetYRot(Entity entity) {
        InterpolationHandler interpolation = entity.getInterpolation();
        return interpolation != null ? interpolation.yRot() : entity.getYRot();
    }
    
    static float getLerpTargetXRot(Entity entity) {
        InterpolationHandler interpolation = entity.getInterpolation();
        return interpolation != null ? interpolation.xRot() : entity.getXRot();
    }
    
    static Vec3 getLerpTarget(Entity entity) {
        InterpolationHandler interpolation = entity.getInterpolation();
        return interpolation != null ? interpolation.position() : entity.position();
    }
    
    static int getRemainingInterpolationSteps(Entity entity) {
        InterpolationHandler interpolation = entity.getInterpolation();
        if (interpolation != null) {
            return ((IEInterpolationHandler) interpolation).ip_getRemainingSteps();
        }
        return getLerpTarget(entity).distanceToSqr(entity.position()) > LERP_TARGET_EPSILON_SQR ?
            INTERPOLATION_STEPS : 0;
    }
    
    /**
     * Updates the entity's portal collision entries.
     * Entries that refer to portals near a previous position can clip the moved entity against the wrong portal.
     */
    static void refreshPortalCollision(Entity entity) {
        PortalCollisionHandler collisionHandler = ((IEEntity) entity).ip_getPortalCollisionHandler();
        if (collisionHandler != null) {
            collisionHandler.update(entity);
        }
    }
    
    private static boolean hasCrossedPlane(Portal portal, Entity entity) {
        return portal.getDistanceToPlane(entity.getEyePosition()) <= 0;
    }
    
    /**
     * Returns whether moving the entity to the source-side target passes through the portal.
     */
    private static boolean passesThroughPortal(Entity entity, Portal portal, Vec3 sourceTarget) {
        Vec3 eyeOffset = entity.getEyePosition().subtract(entity.position());
        return portal.rayTrace(entity.getEyePosition(), sourceTarget.add(eyeOffset)) != null;
    }
    
    /**
     * Returns the portal from the entities of the world or from its global portals.
     * Returns null when no portal with the given UUID is found.
     */
    @Nullable
    private static Portal findPortal(ClientLevel world, int portalId, UUID portalUuid) {
        if (world.getEntity(portalId) instanceof Portal portal && portal.getUUID().equals(portalUuid)) {
            return portal;
        }
        
        List<Portal> globalPortals = CHelper.getClientGlobalPortal(world);
        if (globalPortals == null) return null;
        
        for (Portal globalPortal : globalPortals) {
            if (globalPortal.getUUID().equals(portalUuid)) {
                return globalPortal;
            }
        }
        return null;
    }
    
    private static void applyEntityPosThroughPortal(ResourceKey<Level> dim, int entityId, ServerHandoff handoff) {
        ClientLevel world = ClientWorldLoader.getWorld(dim);
        Entity entity = world.getEntity(entityId);
        if (entity == null) return;
        
        Vec3 curPos = entity.position();
        Vec3 lastTickPos = new Vec3(entity.xo, entity.yo, entity.zo);
        Vec3 lerpTarget = getLerpTarget(entity);
        float lerpYaw = getLerpTargetYRot(entity);
        float lerpPitch = getLerpTargetXRot(entity);
        Vec3 velocity = entity.getDeltaMovement();
        Vec3 newVelocity = handoff.transformVec().apply(velocity);
        if (newVelocity.length() > MAX_HANDOFF_SPEED) {
            newVelocity = newVelocity.normalize().scale(MAX_HANDOFF_SPEED);
        }
        int remainingSteps = lerpTarget.distanceToSqr(curPos) > LERP_TARGET_EPSILON_SQR ?
            getRemainingInterpolationSteps(entity) : 0;
        
        Vec3 mappedCurPos = handoff.transformPoint().apply(curPos);
        double error = mappedCurPos.distanceTo(handoff.pos());
        double allowedError = ERROR_ALLOWANCE_BASE +
            ERROR_ALLOWANCE_PER_SPEED * Math.max(handoff.speed(), velocity.length());
        
        LOGGER.debug("Entity portal update: id={} clientPos={} clientTarget={} mappedPos={} serverPos={} error={} limit={}",
            entityId, curPos, lerpTarget, mappedCurPos, handoff.pos(), error, allowedError);
        
        if (error > allowedError) {
            ClientTeleportationManager.RemoteCallables.updateEntityPos(
                dim, entityId, handoff.pos(), handoff.yaw(), handoff.pitch(), handoff.headYaw()
            );
            entity.lerpMotion(newVelocity.x, newVelocity.y, newVelocity.z);
            return;
        }
        
        float deltaYaw = handoff.yaw() - handoff.oldYaw();
        float deltaPitch = handoff.pitch() - handoff.oldPitch();
        
        McHelper.setPosAndLastTickPos(
            entity,
            mappedCurPos,
            handoff.transformPoint().apply(lastTickPos)
        );
        McHelper.updateBoundingBox(entity);
        refreshPortalCollision(entity);
        
        // This matches the server's reset base before the queued movement is replayed.
        // The render position intentionally retains its interpolation lag.
        entity.syncPacketPositionCodec(handoff.pos().x, handoff.pos().y, handoff.pos().z);
        
        shiftEntityRotation(entity, deltaYaw, deltaPitch);
        
        // Keeps the original interpolation deadline.
        // Restarting it would pull a fast entity toward a stale target after the crossing.
        // A completed interpolation is cleared as well.
        // Its target can equal the current position while a living entity still has steps left.
        if (entity.getInterpolation() != null || remainingSteps > 0) {
            setInterpolationTarget(
                entity, handoff.transformPoint().apply(lerpTarget),
                lerpYaw + deltaYaw, lerpPitch + deltaPitch,
                remainingSteps
            );
        }
        entity.lerpMotion(newVelocity.x, newVelocity.y, newVelocity.z);
    }
    
    /**
     * Adds the rotation the server applied during the teleport to the entity's current and previous rotations.
     */
    private static void shiftEntityRotation(Entity entity, float deltaYaw, float deltaPitch) {
        if (deltaYaw == 0 && deltaPitch == 0) return;
        
        entity.setYRot(entity.getYRot() + deltaYaw);
        entity.yRotO += deltaYaw;
        entity.setXRot(entity.getXRot() + deltaPitch);
        entity.xRotO += deltaPitch;
        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.yHeadRot += deltaYaw;
            livingEntity.yHeadRotO += deltaYaw;
            livingEntity.yBodyRot += deltaYaw;
            livingEntity.yBodyRotO += deltaYaw;
            if (livingEntity instanceof IELivingEntityInterpolation headLerp && headLerp.ip_getLerpHeadSteps() > 0) {
                headLerp.ip_setLerpYHeadRot(headLerp.ip_getLerpYHeadRot() + deltaYaw);
            }
        }
    }
    
    public static class RemoteCallables {
        public static void prepareCrossDimensionEntityTeleport(
            ResourceKey<Level> sourceDimension, ResourceKey<Level> destinationDimension,
            int entityId, int portalId, UUID portalUuid, Vec3 sourceTarget
        ) {
            ClientLevel world = ClientWorldLoader.getWorld(sourceDimension);
            Entity entity = world.getEntity(entityId);
            Portal portal = findPortal(world, portalId, portalUuid);
            if (entity == null || portal == null) return;
            
            boolean alreadyCrossed = hasCrossedPlane(portal, entity);
            if (!alreadyCrossed && !passesThroughPortal(entity, portal, sourceTarget)) return;
            
            PendingCrossDimensionEntityTeleport previous = pendingCrossDimensionEntityTeleports.get(entityId);
            if (previous != null) previous.finish();
            if (!alreadyCrossed && !(entity instanceof ItemEntity)) {
                setInterpolationTarget(entity, sourceTarget, entity.getYRot(), entity.getXRot(), INTERPOLATION_STEPS);
            }
            pendingCrossDimensionEntityTeleports.put(
                entityId, new PendingCrossDimensionEntityTeleport(entity, portal, destinationDimension)
            );
        }
        
        /**
         * Maps the interpolated client state of the entity through a same-dimension portal, preserving smooth motion.
         * Falls back to a plain position update when the mapped state is too far from the server position.
         */
        public static void updateEntityPosThroughPortal(
            ResourceKey<Level> dim, int entityId, int portalId, UUID portalUuid,
            Vec3 serverNewPos, float newYaw, float newPitch, float newHeadYaw,
            float oldYaw, float oldPitch, double serverSpeed
        ) {
            ClientLevel world = ClientWorldLoader.getWorld(dim);
            Entity entity = world.getEntity(entityId);
            if (entity == null) {
                Helper.err("cannot find entity to update position through portal");
                return;
            }
            
            PendingEntityTeleport previous = pendingEntityTeleports.get(entityId);
            if (previous != null) previous.finish();
            
            Portal portal = findPortal(world, portalId, portalUuid);
            if (portal == null) {
                ClientTeleportationManager.RemoteCallables.updateEntityPos(
                    dim, entityId, serverNewPos, newYaw, newPitch, newHeadYaw
                );
                return;
            }
            
            DQuaternion rotation = portal.getRotationD();
            Vec3 originPos = portal.getOriginPos();
            Vec3 destPos = portal.getDestPos();
            double scale = portal.getScale();
            ServerHandoff handoff = new ServerHandoff(
                serverNewPos, newYaw, newPitch, newHeadYaw, oldYaw, oldPitch, serverSpeed,
                p -> rotation.rotate(p.subtract(originPos)).scale(scale).add(destPos),
                v -> rotation.rotate(v).scale(scale)
            );
            
            Vec3 sourceTarget = rotation.getConjugated()
                .rotate(serverNewPos.subtract(destPos).scale(1.0 / scale)).add(originPos);
            if (!hasCrossedPlane(portal, entity) && passesThroughPortal(entity, portal, sourceTarget)) {
                setInterpolationTarget(entity, sourceTarget, entity.getYRot(), entity.getXRot(), INTERPOLATION_STEPS);
                LOGGER.debug("Delaying entity portal handoff: id={} sourcePos={} sourceTarget={} distance={}",
                    entityId, entity.position(), sourceTarget, portal.getDistanceToPlane(entity.getEyePosition()));
                pendingEntityTeleports.put(entityId, new PendingEntityTeleport(
                    entity, portal, () -> applyEntityPosThroughPortal(dim, entityId, handoff)
                ));
                return;
            }
            applyEntityPosThroughPortal(dim, entityId, handoff);
        }
    }
}
