package qouteall.imm_ptl.core.teleportation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.mixin.client.accessor.IEItemEntity;
import qouteall.imm_ptl.core.mixin.client.accessor.IEWalkAnimationState;
import qouteall.imm_ptl.core.portal.Portal;

/**
 * Copies the client render state of an entity to its counterpart on the other side of a portal.
 * The destination entity is a new object, so it would otherwise restart its animations from scratch.
 */
@Environment(EnvType.CLIENT)
final class ClientEntityRenderStateTransfer {
    // The destination entity may already have a later teleport applied.
    // A source mapped farther than this squared distance from it is stale.
    private static final double MAX_STALE_SOURCE_DISTANCE_SQR = 64;
    
    private static final int HEAD_LERP_STEPS = 3;
    
    private ClientEntityRenderStateTransfer() {}
    
    static void transfer(Entity source, Entity destination, Portal portal) {
        Vec3 target = ClientEntityPortalHandoff.getLerpTarget(destination);
        float targetYaw = destination.lerpTargetYRot();
        float targetPitch = destination.lerpTargetXRot();
        float targetHeadYaw = destination.getYHeadRot();
        int remainingSteps = ClientEntityPortalHandoff.getRemainingInterpolationSteps(destination);
        Vec3 mappedPos = portal.transformPoint(source.position());
        
        // A stale source must not pull an entity back from a later teleport.
        if (mappedPos.distanceToSqr(target) > MAX_STALE_SOURCE_DISTANCE_SQR) {
            return;
        }
        
        // An existing server correction is continued.
        // A freely moving entity must not start interpolating toward an out-of-date pre-handoff position.
        if (remainingSteps > 0 && !(destination instanceof ItemEntity)) {
            ClientEntityPortalHandoff.setInterpolationTarget(destination, target, targetYaw, targetPitch, remainingSteps);
        }
        
        transferPosition(source, destination, portal, mappedPos);
        transferRotation(source, destination, portal);
        if (source instanceof LivingEntity oldLiving && destination instanceof LivingEntity newLiving) {
            transferLivingState(oldLiving, newLiving, portal, targetHeadYaw);
        }
        if (source instanceof ItemEntity oldItem && destination instanceof ItemEntity newItem) {
            ((IEItemEntity) newItem).ip_setAge(oldItem.getAge());
            ((IEItemEntity) newItem).ip_setBobOffs(oldItem.bobOffs);
        }
        ClientEntityPortalHandoff.refreshPortalCollision(destination);
    }
    
    private static void transferPosition(Entity source, Entity destination, Portal portal, Vec3 mappedPos) {
        McHelper.setPosAndLastTickPos(destination, mappedPos,
            portal.transformPoint(new Vec3(source.xo, source.yo, source.zo)));
        Vec3 renderOldPos = portal.transformPoint(new Vec3(source.xOld, source.yOld, source.zOld));
        destination.xOld = renderOldPos.x;
        destination.yOld = renderOldPos.y;
        destination.zOld = renderOldPos.z;
        McHelper.updateBoundingBox(destination);
    }
    
    private static void transferRotation(Entity source, Entity destination, Portal portal) {
        destination.setYRot(TeleportationUtil.transformEntityYaw(portal, source.getYRot()));
        destination.yRotO = TeleportationUtil.transformEntityYaw(portal, source.yRotO);
        
        Vec3 look = portal.transformLocalVecNonScale(source.getLookAngle()).normalize();
        Vec3 oldLook = portal.transformLocalVecNonScale(
            Vec3.directionFromRotation(source.xRotO, source.yRotO)
        ).normalize();
        destination.setXRot(pitchOf(look));
        destination.xRotO = pitchOf(oldLook);
    }
    
    private static float pitchOf(Vec3 look) {
        return (float) Math.toDegrees(Math.asin(Math.clamp(-look.y, -1.0, 1.0)));
    }
    
    private static void transferLivingState(
        LivingEntity oldLiving, LivingEntity newLiving, Portal portal, float targetHeadYaw
    ) {
        newLiving.yBodyRot = TeleportationUtil.transformEntityYaw(portal, oldLiving.yBodyRot);
        newLiving.yBodyRotO = TeleportationUtil.transformEntityYaw(portal, oldLiving.yBodyRotO);
        newLiving.yHeadRot = TeleportationUtil.transformEntityYaw(portal, oldLiving.yHeadRot);
        newLiving.yHeadRotO = TeleportationUtil.transformEntityYaw(portal, oldLiving.yHeadRotO);
        newLiving.lerpHeadTo(targetHeadYaw, HEAD_LERP_STEPS);
        
        // The destination entity is new, so its leg swing would otherwise restart from zero.
        IEWalkAnimationState oldWalk = (IEWalkAnimationState) oldLiving.walkAnimation;
        IEWalkAnimationState newWalk = (IEWalkAnimationState) newLiving.walkAnimation;
        newWalk.ip_setSpeed(oldLiving.walkAnimation.speed());
        newWalk.ip_setSpeedOld(oldWalk.ip_getSpeedOld());
        newWalk.ip_setPosition(oldWalk.ip_getPosition());
    }
}
