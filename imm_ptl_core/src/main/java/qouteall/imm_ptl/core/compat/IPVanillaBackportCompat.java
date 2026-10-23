package qouteall.imm_ptl.core.compat;

import com.mojang.datafixers.util.Pair;
import net.minecraft.util.Mth;
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
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalPlaceholderBlock;
import qouteall.imm_ptl.core.portal.PortalUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Provides portal-aware target selection and knockback handling for Vanilla Backport spear attacks.
 */
public class IPVanillaBackportCompat {
    private record HitPathRecord(long gameTime, float attackerYaw, List<Portal> path) {}
    
    private static final Map<Entity, HitPathRecord> HIT_PATHS =
        Collections.synchronizedMap(new WeakHashMap<>());
    
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
                    HIT_PATHS.put(entity, new HitPathRecord(level.getGameTime(), source.getYRot(), path));
                }
                else if (path == null || path.isEmpty()) {
                    HIT_PATHS.remove(entity);
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
        
        Optional<Pair<Portal, Vec3>> portalHit = raytracePortal(world, source, start, end);
        
        BlockHitResult blockHit = world.clip(new ClipContext(
            start, end, clipType, ClipContext.Fluid.NONE, source
        ));
        boolean placeholderHit = PortalPlaceholderBlock.isHitOnPlaceholder(blockHit, world);
        boolean blockFound = blockHit.getType() == HitResult.Type.BLOCK && !placeholderHit;
        double blockDist = blockFound ? blockHit.getLocation().distanceTo(start) : Double.MAX_VALUE;
        
        Portal nextPortal = null;
        Vec3 nextPortalHit = null;
        double endDist = length;
        if (portalHit.isPresent()) {
            double portalDist = portalHit.get().getSecond().distanceTo(start);
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
        
        int hitsBefore = out.size();
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
        Vec3 newStart = nextPortal.transformPoint(nextPortalHit)
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
                pathOfEntity.put(entity, new ArrayList<>(path));
            }
        }
    }
    
    private static Vec3 toAttackerFrame(List<Portal> path, Vec3 pos) {
        for (int i = path.size() - 1; i >= 0; i--) {
            pos = path.get(i).inverseTransformPoint(pos);
        }
        return pos;
    }
    
    private static Optional<Pair<Portal, Vec3>> raytracePortal(
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
    
    private static boolean applyingKnockbackFix = false;
    
    /**
     * @return The corrected {@code (x, z)} direction, or {@code null} if no portal hit applies.
     */
    @Nullable
    public static double[] getFixedKnockbackDirection(Entity target, double x, double z) {
        if (applyingKnockbackFix) {
            return null;
        }
        
        HitPathRecord record = HIT_PATHS.get(target);
        if (record == null) {
            return null;
        }
        
        if (Math.abs(target.level().getGameTime() - record.gameTime()) > 2) {
            HIT_PATHS.remove(target);
            return null;
        }
        
        // Only fix the knockback that is based on the attacker's yaw
        double yawRad = record.attackerYaw() * Mth.DEG_TO_RAD;
        if (Math.abs(x - Mth.sin((float) yawRad)) > 1.0E-3
            || Math.abs(z - (-Mth.cos((float) yawRad))) > 1.0E-3
        ) {
            return null;
        }
        
        // The direction the target gets pushed in (knockback pushes along -(x, z))
        Vec3 pushDir = new Vec3(-Mth.sin((float) yawRad), 0, Mth.cos((float) yawRad));
        for (Portal portal : record.path()) {
            pushDir = portal.transformLocalVecNonScale(pushDir);
        }
        
        if (pushDir.x * pushDir.x + pushDir.z * pushDir.z < 1.0E-8) {
            return null;
        }
        
        return new double[]{-pushDir.x, -pushDir.z};
    }
    
    public static void setApplyingKnockbackFix(boolean value) {
        applyingKnockbackFix = value;
    }
}
