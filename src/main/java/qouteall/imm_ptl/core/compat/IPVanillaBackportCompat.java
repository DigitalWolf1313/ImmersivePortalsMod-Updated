package qouteall.imm_ptl.core.compat;

import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalUtils;
import qouteall.q_misc_util.my_util.RayTraceResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Provides portal-aware target selection and attack handling for Vanilla Backport spear attacks.
 */
public class IPVanillaBackportCompat {
    
    private record HitPathRecord(long gameTime, List<Portal> path) {}
    
    /**
     * Maps each target to its recorded hit paths by attacker UUID.
     * The values never reference the target, so the weak keys can be collected.
     */
    private static final Map<Entity, Map<UUID, HitPathRecord>> HIT_PATHS = new WeakHashMap<>();
    
    /**
     * @return {@code null} when no portal is crossed; otherwise the hits along the portal path,
     * which may be empty.
     */
    @Nullable
    public static Collection<EntityHitResult> portalAwareHitEntities(
        Entity source, Vec3 origin, Vec3 from, Predicate<Entity> matching,
        Vec3 to, float entityMargin, ClipContext.Block clipType
    ) {
        Level level = source.level();
        
        if (raytracePortal(level, source, origin, to).isEmpty()) {
            return null;
        }
        
        List<EntityHitResult> out = new ArrayList<>();
        Map<Entity, List<Portal>> pathOfEntity = new HashMap<>();
        
        walk(
            level, source, origin, to, from,
            matching, entityMargin, clipType,
            new ArrayList<>(), out, pathOfEntity
        );
        
        if (!level.isClientSide()) {
            Set<Entity> recorded = new HashSet<>();
            for (EntityHitResult hit : out) {
                Entity entity = hit.getEntity();
                List<Portal> path = pathOfEntity.get(entity);
                if (path != null && !path.isEmpty() && recorded.add(entity)) {
                    recordHitPath(entity, source, level.getGameTime(), path);
                }
                else if (path == null || path.isEmpty()) {
                    takeHitPath(entity, source);
                }
            }
        }
        
        return out;
    }
    
    private static void walk(
        Level world, Entity source,
        Vec3 start, Vec3 end, Vec3 minPoint,
        Predicate<Entity> matching, float entityMargin, ClipContext.Block clipType,
        List<Portal> path, List<EntityHitResult> out, Map<Entity, List<Portal>> pathOfEntity
    ) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 1.0E-6) {
            return;
        }
        Vec3 dir = delta.scale(1.0 / length);
        
        Optional<Pair<Portal, RayTraceResult>> portalHit = raytracePortal(world, source, start, end);
        
        BlockHitResult blockHit = world.clip(new ClipContext(
            start, end, clipType, ClipContext.Fluid.NONE, source
        ));
        boolean blockFound = blockHit.getType() == HitResult.Type.BLOCK;
        double blockDist = blockFound ? blockHit.getLocation().distanceTo(start) : Double.MAX_VALUE;
        
        Portal nextPortal = null;
        RayTraceResult nextPortalHit = null;
        double endDist = length;
        if (portalHit.isPresent()) {
            double portalDist = portalHit.get().getSecond().hitPos().distanceTo(start);
            // Allow a little leniency so a portal flush against a block surface still counts
            if (portalDist < blockDist + 1.0E-4) {
                nextPortal = portalHit.get().getFirst();
                nextPortalHit = portalHit.get().getSecond();
                endDist = portalDist;
            }
        }
        if (nextPortal == null && blockFound) {
            endDist = blockDist;
        }
        
        // Where the min reach dead zone ends along this segment. It can be at or before the
        // segment start if the dead zone was already used up before the portal.
        double minDist = Math.max(0.0, minPoint.subtract(start).dot(dir));
        
        if (endDist > minDist + 1.0E-9) {
            collectEntities(
                world, source,
                start.add(dir.scale(minDist)), start.add(dir.scale(endDist)),
                matching, entityMargin, clipType, path, out, pathOfEntity
            );
        }
        
        if (nextPortal == null || path.size() >= 5) {
            return;
        }
        
        Level destWorld = nextPortal.getDestinationWorld();
        if (destWorld == null) {
            return;
        }
        
        // Start a little inside the destination so the ray does not begin on the exit portal.
        Vec3 newStart = nextPortal.transformPoint(nextPortalHit.hitPos())
            .add(nextPortal.transformLocalVecNonScale(dir).scale(0.001));
        
        path.add(nextPortal);
        walk(
            destWorld, source,
            newStart, nextPortal.transformPoint(end), nextPortal.transformPoint(minPoint),
            matching, entityMargin, clipType,
            path, out, pathOfEntity
        );
        path.remove(path.size() - 1);
    }
    
    private static void collectEntities(
        Level world, Entity source,
        Vec3 from, Vec3 to,
        Predicate<Entity> matching, float entityMargin, ClipContext.Block clipType,
        List<Portal> path, List<EntityHitResult> out, Map<Entity, List<Portal>> pathOfEntity
    ) {
        AABB searchArea = new AABB(from, to).inflate(1.0 + entityMargin);
        
        for (Entity entity : world.getEntities(
            source, searchArea, e -> !(e instanceof Portal) && matching.test(e)
        )) {
            if (pathOfEntity.containsKey(entity)) {
                continue;
            }
            
            AABB box = entity.getBoundingBox();
            Vec3 hitPos = null;
            
            if (box.contains(from)) {
                hitPos = from;
            }
            else {
                Optional<Vec3> exactHit = box.clip(from, to);
                if (exactHit.isPresent()) {
                    hitPos = exactHit.get();
                }
                else if (entityMargin > 0.0F) {
                    Optional<Vec3> outsideHit = box.inflate(entityMargin).clip(from, to);
                    if (outsideHit.isPresent()) {
                        Vec3 outsidePos = outsideHit.get();
                        Vec3 towards = box.getCenter();
                        BlockHitResult blocked = world.clip(new ClipContext(
                            outsidePos, towards, clipType, ClipContext.Fluid.NONE, source
                        ));
                        if (blocked.getType() != HitResult.Type.MISS) {
                            towards = blocked.getLocation();
                        }
                        hitPos = box.clip(outsidePos, towards).orElse(null);
                    }
                }
            }
            
            if (hitPos != null) {
                out.add(new EntityHitResult(entity, toAttackerFrame(path, hitPos)));
                pathOfEntity.put(entity, List.copyOf(path));
            }
        }
    }
    
    private static Vec3 toAttackerFrame(List<Portal> path, Vec3 pos) {
        for (int i = path.size() - 1; i >= 0; i--) {
            pos = path.get(i).inverseTransformPoint(pos);
        }
        return pos;
    }
    
    private static Optional<Pair<Portal, RayTraceResult>> raytracePortal(
        Level world, Entity source, Vec3 from, Vec3 to
    ) {
        return PortalUtils.raytracePortals(
            world, from, to, true,
            portal -> {
                if (source instanceof Player player) {
                    return portal.isInteractableBy(player);
                }
                else {
                    return portal.isVisible();
                }
            }
        );
    }
    
    private static void recordHitPath(
        Entity target, Entity attacker, long gameTime, List<Portal> path
    ) {
        synchronized (HIT_PATHS) {
            Map<UUID, HitPathRecord> byAttacker =
                HIT_PATHS.computeIfAbsent(target, k -> new HashMap<>());
            byAttacker.values().removeIf(record -> record.gameTime() != gameTime);
            byAttacker.put(attacker.getUUID(), new HitPathRecord(gameTime, path));
        }
    }
    
    /**
     * Removes and returns the attacker's record for the target.
     * Returns {@code null} when there is none.
     */
    @Nullable
    private static HitPathRecord takeHitPath(Entity target, Entity attacker) {
        synchronized (HIT_PATHS) {
            Map<UUID, HitPathRecord> byAttacker = HIT_PATHS.get(target);
            if (byAttacker == null) {
                return null;
            }
            
            HitPathRecord record = byAttacker.remove(attacker.getUUID());
            if (byAttacker.isEmpty()) {
                HIT_PATHS.remove(target);
            }
            return record;
        }
    }
    
    /**
     * Runs a Vanilla Backport target attack with its attacker relocated through the recorded
     * portal path, allowing Vanilla Backport to calculate knockback from the transformed yaw.
     */
    public static boolean withAttackerRelocatedForHit(
        Entity attacker, Entity target, BooleanSupplier action
    ) {
        Level level = attacker.level();
        if (level.isClientSide()) {
            return action.getAsBoolean();
        }
        
        HitPathRecord record = takeHitPath(target, attacker);
        // A record only applies to the tick in which its raytrace happened.
        if (record == null || record.gameTime() != level.getGameTime()) {
            return action.getAsBoolean();
        }
        
        return withAttackerRelocated(attacker, record.path(), target.level(), action);
    }
    
    private static boolean withAttackerRelocated(
        Entity attacker, List<Portal> path, Level targetLevel, BooleanSupplier action
    ) {
        IEEntity ieAttacker = (IEEntity) attacker;
        Level oldLevel = attacker.level();
        Vec3 oldPos = attacker.position();
        float oldYaw = attacker.getYRot();
        float oldPitch = attacker.getXRot();
        float oldHeadYaw = attacker.getYHeadRot();
        
        Vec3 newEyePos = attacker.getEyePosition();
        Vec3 newLook = attacker.getLookAngle();
        for (Portal portal : path) {
            newEyePos = portal.transformPoint(newEyePos);
            newLook = portal.transformLocalVecNonScale(newLook);
        }
        newLook = newLook.normalize();
        float newYaw = (float) Math.toDegrees(Math.atan2(-newLook.x, newLook.z));
        float newPitch = (float) Math.toDegrees(Math.asin(Math.clamp(-newLook.y, -1.0, 1.0)));
        Vec3 newPos = newEyePos.subtract(0, attacker.getEyeHeight(), 0);
        
        try {
            // The raw setter does not notify the level, so the attacker stays in its old entity section.
            ieAttacker.ip_setPositionWithoutTriggeringCallback(newPos);
            attacker.setYRot(newYaw);
            attacker.setXRot(newPitch);
            attacker.setYHeadRot(newYaw);
            if (oldLevel != targetLevel) {
                ieAttacker.ip_setWorld(targetLevel);
            }
            return action.getAsBoolean();
        }
        finally {
            if (oldLevel != targetLevel) {
                ieAttacker.ip_setWorld(oldLevel);
            }
            ieAttacker.ip_setPositionWithoutTriggeringCallback(oldPos);
            attacker.setYRot(oldYaw);
            attacker.setXRot(oldPitch);
            attacker.setYHeadRot(oldHeadYaw);
        }
    }
}
