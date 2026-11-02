package qouteall.imm_ptl.core.teleportation;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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
import qouteall.imm_ptl.core.ducks.IEEntityInterpolation;
import qouteall.imm_ptl.core.mixin.client.accessor.IEItemEntity;
import qouteall.imm_ptl.core.mixin.client.accessor.IELivingEntityInterpolation;
import qouteall.imm_ptl.core.mixin.client.accessor.IEWalkAnimationState;
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
    
    private static final Map<Integer, PendingEntityTeleport> pendingEntityTeleports = new HashMap<>();
    private static final Map<Integer, PendingCrossDimensionEntityTeleport> pendingCrossDimensionEntityTeleports = new HashMap<>();
    
    private static class PendingCrossDimensionEntityTeleport {
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
        
        void finish() {
            pendingCrossDimensionEntityTeleports.remove(sourceEntity.getId(), this);
            ClientLevel destinationWorld = ClientWorldLoader.getOptionalWorld(destination);
            Entity destinationEntity = destinationWorld == null ? null : destinationWorld.getEntity(sourceEntity.getId());
            if (!sourceEntity.isRemoved() && !portal.isRemoved() && destinationEntity != null &&
                destinationEntity.getUUID().equals(sourceEntity.getUUID())) {
                transferEntityRenderState(sourceEntity, destinationEntity, portal);
            }
            ClientLevel sourceWorld = (ClientLevel) sourceEntity.level();
            if (removalReceived && sourceWorld.getEntity(sourceEntity.getId()) == sourceEntity) {
                sourceWorld.removeEntity(sourceEntity.getId(), Entity.RemovalReason.CHANGED_DIMENSION);
            }
        }
    }
    
    private static class PendingEntityTeleport {
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
        
        void finish() {
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
        if (!pendingCrossDimensionEntityTeleports.isEmpty()) {
            for (PendingCrossDimensionEntityTeleport pending : new ArrayList<>(pendingCrossDimensionEntityTeleports.values())) {
                ++pending.ticks;
                ClientLevel destinationWorld = ClientWorldLoader.getOptionalWorld(pending.destination);
                Entity destinationEntity = destinationWorld == null ? null : destinationWorld.getEntity(pending.sourceEntity.getId());
                boolean destinationReady = destinationEntity != null &&
                    destinationEntity.getUUID().equals(pending.sourceEntity.getUUID());
                boolean crossed = pending.portal.getDistanceToPlane(pending.sourceEntity.getEyePosition()) <= 0;
                if (pending.sourceEntity.isRemoved() || pending.portal.isRemoved() ||
                    (crossed && destinationReady) || pending.ticks >= MAX_HANDOFF_TICKS) {
                    // The transferred render history already covers the crossing frame.
                    // Waiting another tick would let the source reach its lerp target and visibly stop.
                    pending.finish();
                }
            }
        }
        
        if (!pendingEntityTeleports.isEmpty()) {
            for (PendingEntityTeleport pending : new ArrayList<>(pendingEntityTeleports.values())) {
                if (pending.entity.isRemoved() || pending.portal.isRemoved() ||
                    pending.portal.getDistanceToPlane(pending.entity.getEyePosition()) <= 0 ||
                    ++pending.ticks >= MAX_HANDOFF_TICKS) {
                    pending.finish();
                }
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
        entity.lerpTo(target.x, target.y, target.z, yaw, pitch, steps, false);
        if (entity instanceof IEEntityInterpolation interpolation) {
            // Boats and minecarts use a fixed or adjusted interpolation duration.
            interpolation.ip_setLerpSteps(steps);
        }
    }
    
    private static int getRemainingInterpolationSteps(Entity entity) {
        if (entity instanceof IEEntityInterpolation interpolation) {
            return interpolation.ip_getLerpSteps();
        }
        return 0;
    }
    
    private static Vec3 getInterpolationTarget(Entity entity) {
        if (entity instanceof IEEntityInterpolation interpolation) {
            return new Vec3(interpolation.ip_getLerpX(), interpolation.ip_getLerpY(), interpolation.ip_getLerpZ());
        }
        return entity.position();
    }
    
    private static float getInterpolationTargetYaw(Entity entity) {
        return entity instanceof IEEntityInterpolation interpolation ?
            (float) interpolation.ip_getLerpYRot() : entity.getYRot();
    }
    
    private static float getInterpolationTargetPitch(Entity entity) {
        return entity instanceof IEEntityInterpolation interpolation ?
            (float) interpolation.ip_getLerpXRot() : entity.getXRot();
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
    
    private static void transferEntityRenderState(Entity source, Entity destination, Portal portal) {
        Vec3 target = getInterpolationTarget(destination);
        float targetYaw = getInterpolationTargetYaw(destination);
        float targetPitch = getInterpolationTargetPitch(destination);
        float targetHeadYaw = destination.getYHeadRot();
        int remainingSteps = getRemainingInterpolationSteps(destination);
        Vec3 mappedPos = portal.transformPoint(source.position());
        
        // A stale source must not pull an entity back from a later teleport.
        if (mappedPos.distanceToSqr(target) > 64) {
            return;
        }
        
        // An existing server correction is continued.
        // A freely moving entity must not start interpolating toward an out-of-date pre-handoff position.
        if (remainingSteps > 0 && !(destination instanceof ItemEntity)) {
            setInterpolationTarget(destination, target, targetYaw, targetPitch, remainingSteps);
        }
        
        McHelper.setPosAndLastTickPos(destination, mappedPos,
            portal.transformPoint(new Vec3(source.xo, source.yo, source.zo)));
        Vec3 renderOldPos = portal.transformPoint(new Vec3(source.xOld, source.yOld, source.zOld));
        destination.xOld = renderOldPos.x;
        destination.yOld = renderOldPos.y;
        destination.zOld = renderOldPos.z;
        McHelper.updateBoundingBox(destination);
        
        destination.setYRot(TeleportationUtil.transformEntityYaw(portal, source.getYRot()));
        destination.yRotO = TeleportationUtil.transformEntityYaw(portal, source.yRotO);
        Vec3 look = portal.transformLocalVecNonScale(source.getLookAngle()).normalize();
        Vec3 oldLook = portal.transformLocalVecNonScale(
            Vec3.directionFromRotation(source.xRotO, source.yRotO)
        ).normalize();
        destination.setXRot((float) Math.toDegrees(Math.asin(Mth.clamp(-look.y, -1.0, 1.0))));
        destination.xRotO = (float) Math.toDegrees(Math.asin(Mth.clamp(-oldLook.y, -1.0, 1.0)));
        if (source instanceof LivingEntity oldLiving && destination instanceof LivingEntity newLiving) {
            newLiving.yBodyRot = TeleportationUtil.transformEntityYaw(portal, oldLiving.yBodyRot);
            newLiving.yBodyRotO = TeleportationUtil.transformEntityYaw(portal, oldLiving.yBodyRotO);
            newLiving.yHeadRot = TeleportationUtil.transformEntityYaw(portal, oldLiving.yHeadRot);
            newLiving.yHeadRotO = TeleportationUtil.transformEntityYaw(portal, oldLiving.yHeadRotO);
            newLiving.lerpHeadTo(targetHeadYaw, 3);
            
            // The destination entity is new, so its leg swing would otherwise restart from zero.
            IEWalkAnimationState oldWalk = (IEWalkAnimationState) oldLiving.walkAnimation;
            IEWalkAnimationState newWalk = (IEWalkAnimationState) newLiving.walkAnimation;
            newWalk.ip_setSpeed(oldLiving.walkAnimation.speed());
            newWalk.ip_setSpeedOld(oldWalk.ip_getSpeedOld());
            newWalk.ip_setPosition(oldWalk.ip_getPosition());
        }
        if (source instanceof ItemEntity oldItem && destination instanceof ItemEntity newItem) {
            ((IEItemEntity) newItem).ip_setAge(oldItem.getAge());
            ((IEItemEntity) newItem).ip_setBobOffs(oldItem.bobOffs);
        }
        PortalCollisionHandler collisionHandler = ((IEEntity) destination).ip_getPortalCollisionHandler();
        if (collisionHandler != null) {
            collisionHandler.update(destination);
        }
    }
    
    private static void applyEntityPosThroughPortal(ResourceKey<Level> dim, int entityId, ServerHandoff handoff) {
        ClientLevel world = ClientWorldLoader.getWorld(dim);
        Entity entity = world.getEntity(entityId);
        if (entity == null) return;
        
        Vec3 curPos = entity.position();
        Vec3 lastTickPos = new Vec3(entity.xo, entity.yo, entity.zo);
        Vec3 lerpTarget = getInterpolationTarget(entity);
        float lerpYaw = getInterpolationTargetYaw(entity);
        float lerpPitch = getInterpolationTargetPitch(entity);
        Vec3 velocity = entity.getDeltaMovement();
        Vec3 newVelocity = handoff.transformVec().apply(velocity);
        if (newVelocity.length() > 15) {
            newVelocity = newVelocity.normalize().scale(15);
        }
        int remainingSteps = lerpTarget.distanceToSqr(curPos) > 1.0E-12 ?
            getRemainingInterpolationSteps(entity) : 0;
        
        Vec3 mappedCurPos = handoff.transformPoint().apply(curPos);
        double error = mappedCurPos.distanceTo(handoff.pos());
        // Interpolation lag is allowed for.
        // A larger error can mean that the entity was just tracked.
        double allowedError = 8 + 20 * Math.max(handoff.speed(), velocity.length());
        
        LOGGER.info("Entity portal update: id={} clientPos={} clientTarget={} mappedPos={} serverPos={} error={} limit={}",
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
        // Collision entries refer to portals near the old position.
        // Keeping them until the next entity tick can clip the moved entity against the source portal.
        PortalCollisionHandler collisionHandler = ((IEEntity) entity).ip_getPortalCollisionHandler();
        if (collisionHandler != null) {
            collisionHandler.update(entity);
        }
        
        // This matches the server's reset base before the queued movement is replayed.
        // The render position intentionally retains its interpolation lag.
        entity.syncPacketPositionCodec(handoff.pos().x, handoff.pos().y, handoff.pos().z);
        
        if (deltaYaw != 0 || deltaPitch != 0) {
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
        
        // Keeps the original interpolation deadline.
        // Restarting it would pull a fast entity toward a stale target after the crossing.
        // A completed interpolation is cleared as well.
        // Its target can equal the current position while a living entity still has steps left.
        if (entity instanceof IEEntityInterpolation || remainingSteps > 0) {
            setInterpolationTarget(
                entity, handoff.transformPoint().apply(lerpTarget),
                lerpYaw + deltaYaw, lerpPitch + deltaPitch,
                remainingSteps
            );
        }
        entity.lerpMotion(newVelocity.x, newVelocity.y, newVelocity.z);
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
            
            Vec3 eyeOffset = entity.getEyePosition().subtract(entity.position());
            boolean alreadyCrossed = portal.getDistanceToPlane(entity.getEyePosition()) <= 0;
            if (!alreadyCrossed && portal.rayTrace(entity.getEyePosition(), sourceTarget.add(eyeOffset)) == null) return;
            
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
            Vec3 eyeOffset = entity.getEyePosition().subtract(entity.position());
            if (portal.getDistanceToPlane(entity.getEyePosition()) > 0 &&
                portal.rayTrace(entity.getEyePosition(), sourceTarget.add(eyeOffset)) != null) {
                setInterpolationTarget(entity, sourceTarget, entity.getYRot(), entity.getXRot(), INTERPOLATION_STEPS);
                LOGGER.info("Delaying entity portal handoff: id={} sourcePos={} sourceTarget={} distance={}",
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
