
package qouteall.imm_ptl.core.teleportation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalState;
import qouteall.imm_ptl.core.portal.animation.ClientPortalAnimationManagement;
import qouteall.q_misc_util.my_util.RayTraceResult;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides the portal lookups used by particles that move through portals.
 * Particles only tick in the world that is current for the particle engine,
 * so the cache only holds the portals of that one world.
 */
public class ParticleTeleportation {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParticleTeleportation.class);
    
    private static final int CACHE_RANGE = 48;
    
    // This matches the range that ClientWorldLoader uses to decide whether a remote world's particles tick.
    private static final int REMOTE_TICK_RANGE = 10;
    
    private static final int MAX_CHAINED_CROSSINGS = 2;
    
    private static final long ONE_TICK_NANOS = 50_000_000L;
    
    // The exit position is moved this far into the destination to avoid hitting the reverse portal.
    private static final double EXIT_NUDGE = 0.002;
    
    private static @Nullable ClientLevel cachedLevel = null;
    private static List<Portal> cachedPortals = Collections.emptyList();
    private static Map<Portal, PortalMotion> cachedMotions = Collections.emptyMap();
    private static Set<ResourceKey<Level>> tickedRemoteDimensions = Collections.emptySet();
    
    private static int crossingDepth = 0;
    
    /**
     * The states of an animating portal at the start and at the end of the tick that is being processed.
     * The bounds contain the portal's surface during the whole tick.
     */
    private record PortalMotion(PortalState start, PortalState end, AABB bounds) {}
    
    /**
     * Describes where and how a particle comes out of a portal.
     * The progress is the fraction of the particle's movement that happens before the crossing.
     */
    public record Crossing(
        Portal portal, double progress,
        Vec3 exitCenter, Vec3 newOldCenter,
        Vec3 newVelocity, Vec3 newRemainingMotion
    ) {}
    
    /**
     * Rebuilds the portal cache for the world that the particle engine is about to tick.
     */
    public static void refresh(ClientLevel level) {
        cachedLevel = level;
        cachedPortals = Collections.emptyList();
        cachedMotions = Collections.emptyMap();
        
        if (!IPGlobal.particlesCrossPortals) {
            return;
        }
        
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        
        boolean isPlayerWorld = level == player.level();
        
        // The camera is moved next to the destination portal while a remote world ticks.
        Vec3 center = isPlayerWorld ?
            player.position() : client.gameRenderer.getMainCamera().getPosition();
        
        List<Portal> portals = IPMcHelper.getNearbyPortalList(
            level, center, CACHE_RANGE, Portal::getTeleportable
        );
        if (!portals.isEmpty()) {
            cachedPortals = portals;
            cachedMotions = collectMotions(portals);
        }
        
        if (isPlayerWorld) {
            Set<ResourceKey<Level>> dimensions = new HashSet<>();
            IPMcHelper.getNearbyPortalList(
                level, player.position(), REMOTE_TICK_RANGE, p -> true
            ).forEach(p -> dimensions.add(p.getDestDim()));
            tickedRemoteDimensions = dimensions;
        }
    }
    
    /**
     * Returns the cached portals when the level is the one that was last refreshed.
     * Returns an empty list otherwise.
     */
    public static List<Portal> getPortals(ClientLevel level) {
        if (level != cachedLevel) {
            return Collections.emptyList();
        }
        return cachedPortals;
    }
    
    /**
     * A particle that enters a remote dimension that is not ticked would stay frozen there.
     * Only the current dimension and dimensions near a portal within the player's range tick.
     */
    public static boolean isDimensionTicked(ResourceKey<Level> dimension) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.level().dimension() == dimension) {
            return true;
        }
        return tickedRemoteDimensions.contains(dimension);
    }
    
    public static boolean canCrossMore() {
        return crossingDepth < MAX_CHAINED_CROSSINGS;
    }
    
    public static void enterCrossing() {
        crossingDepth++;
    }
    
    public static void exitCrossing() {
        crossingDepth--;
    }
    
    private static Map<Portal, PortalMotion> collectMotions(List<Portal> portals) {
        Map<Portal, PortalMotion> motions = null;
        for (Portal portal : portals) {
            PortalMotion motion = getMotion(portal);
            if (motion == null) {
                continue;
            }
            if (motions == null) {
                motions = new IdentityHashMap<>();
            }
            motions.put(portal, motion);
        }
        return motions == null ? Collections.emptyMap() : motions;
    }
    
    private static @Nullable PortalMotion getMotion(Portal portal) {
        PortalState start = portal.animation.lastTickAnimatedState;
        PortalState end = portal.animation.thisTickAnimatedState;
        
        if (start == null || end == null) {
            // A default animation is driven by real time instead of ticks.
            start = ClientPortalAnimationManagement.getDefaultAnimationState(portal, 0);
            end = ClientPortalAnimationManagement.getDefaultAnimationState(portal, ONE_TICK_NANOS);
        }
        
        if (start == null || end == null) {
            return null;
        }
        if (start.fromWorld != end.fromWorld || start.toWorld != end.toWorld) {
            return null;
        }
        
        AABB bounds = getSurfaceBounds(start).minmax(getSurfaceBounds(end));
        return new PortalMotion(start, end, bounds);
    }
    
    // The radius is generous so that it does not depend on where the portal's origin is.
    private static AABB getSurfaceBounds(PortalState state) {
        double radius = Math.sqrt(state.width * state.width + state.height * state.height)
            + state.thickness + 0.5;
        return new AABB(state.fromPos, state.fromPos).inflate(radius);
    }
    
    /**
     * Returns the crossing when the particle's movement from one position to another goes through the portal.
     * An animating portal can also move through a particle that does not move.
     * The portal's own movement only counts for the first crossing of a particle's movement.
     */
    @Nullable
    public static Crossing check(
        Portal portal, AABB box, Vec3 from, Vec3 to, Vec3 velocity, Vec3 oldCenter
    ) {
        PortalMotion motion = crossingDepth == 0 ? cachedMotions.get(portal) : null;
        if (motion != null) {
            return checkMoving(portal, motion, box, from, to, velocity, oldCenter);
        }
        return checkStatic(portal, box, from, to, velocity, oldCenter);
    }
    
    @Nullable
    private static Crossing checkStatic(
        Portal portal, AABB box, Vec3 from, Vec3 to, Vec3 velocity, Vec3 oldCenter
    ) {
        if (from.equals(to)) {
            return null;
        }
        
        AABB sweptBox = box.expandTowards(to.subtract(from)).inflate(0.1);
        if (!portal.getBoundingBox().intersects(sweptBox)) {
            return null;
        }
        
        Vec3 hit = portal.rayTrace(from, to);
        if (hit == null) {
            return null;
        }
        
        double progress = hit.distanceTo(from) / to.distanceTo(from);
        
        // The content direction in the destination points away from the destination portal.
        Vec3 exitCenter = portal.transformPoint(hit)
            .add(portal.getContentDirection().normalize().scale(EXIT_NUDGE));
        
        return new Crossing(
            portal, progress,
            exitCenter,
            // The previous position is transformed too, so that render interpolation stays continuous.
            portal.transformPoint(oldCenter),
            portal.transformLocalVec(velocity),
            portal.transformLocalVec(to.subtract(hit))
        );
    }
    
    /**
     * Traces the particle in the portal-local space that moves with the portal.
     * The velocities of the portal's two sides are taken into account,
     * so a portal that sweeps over a particle gives the particle the relative velocity.
     */
    @Nullable
    private static Crossing checkMoving(
        Portal portal, PortalMotion motion, AABB box,
        Vec3 from, Vec3 to, Vec3 velocity, Vec3 oldCenter
    ) {
        AABB sweptBox = box.expandTowards(to.subtract(from)).inflate(0.1);
        if (!motion.bounds().intersects(sweptBox)) {
            return null;
        }
        
        PortalState start = motion.start();
        PortalState end = motion.end();
        
        RayTraceResult result = portal.getPortalShape().raytracePortalShapeByLocalPos(
            portal.getThisSideState(),
            start.worldPosToPortalLocalPos(from),
            end.worldPosToPortalLocalPos(to),
            0
        );
        if (result == null) {
            return null;
        }
        
        double t = result.t();
        double remainingFraction = 1 - t;
        Vec3 localHit = result.hitPos();
        
        PortalState collisionState = PortalState.interpolate(start, end, t, false);
        Vec3 worldHit = collisionState.portalLocalPosToWorldPos(localHit);
        
        TeleportationUtil.PortalPointVelocity pointVelocity =
            TeleportationUtil.getPortalPointVelocity(start, end, localHit);
        Vec3 thisSideVelocity = pointVelocity.thisSidePointVelocity();
        Vec3 otherSideVelocity = pointVelocity.otherSidePointVelocity();
        
        Vec3 newVelocity = collisionState
            .transformVec(velocity.subtract(thisSideVelocity))
            .add(otherSideVelocity);
        
        Vec3 remainingMotion = to.subtract(from).scale(remainingFraction);
        Vec3 newRemainingMotion = collisionState
            .transformVec(remainingMotion.subtract(thisSideVelocity.scale(remainingFraction)))
            .add(otherSideVelocity.scale(remainingFraction));
        
        Vec3 exitCenter = collisionState.transformPoint(worldHit)
            .add(collisionState.getContentDirection().normalize().scale(EXIT_NUDGE));
        
        if (portal.getPortalShape().isPlanar()) {
            // The destination portal may move toward the particle during the rest of the tick.
            Vec3 contentDirection = end.getContentDirection();
            Vec3 endPos = exitCenter.add(newRemainingMotion);
            double depth = endPos.subtract(end.toPos).dot(contentDirection);
            if (depth < EXIT_NUDGE) {
                newRemainingMotion = newRemainingMotion.add(contentDirection.scale(EXIT_NUDGE - depth));
            }
        }
        
        return new Crossing(
            portal, t,
            exitCenter,
            start.transformPoint(oldCenter),
            newVelocity,
            newRemainingMotion
        );
    }
}
