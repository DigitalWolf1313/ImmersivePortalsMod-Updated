package qouteall.imm_ptl.core.teleportation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
    
    private static @Nullable ClientLevel cachedLevel = null;
    private static List<Portal> cachedPortals = Collections.emptyList();
    private static Set<ResourceKey<Level>> tickedRemoteDimensions = Collections.emptySet();
    
    private static int crossingDepth = 0;
    
    /**
     * Rebuilds the portal cache for the world that the particle engine is about to tick.
     */
    public static void refresh(ClientLevel level) {
        cachedLevel = level;
        cachedPortals = Collections.emptyList();
        
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
}
