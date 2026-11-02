package qouteall.imm_ptl.core.teleportation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.portal.Portal;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ParticleTeleportation {
    private static final int CACHE_RANGE = 48;
    private static final int REMOTE_TICK_RANGE = 10;
    private static final int MAX_CHAINED_CROSSINGS = 2;
    
    private static @Nullable ClientLevel cachedLevel = null;
    private static List<Portal> cachedPortals = Collections.emptyList();
    private static Set<ResourceKey<Level>> tickedRemoteDimensions = Collections.emptySet();
    
    private static int crossingDepth = 0;
    
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
                level, player.position(), REMOTE_TICK_RANGE, portal -> true
            ).forEach(portal -> dimensions.add(portal.getDestDim()));
            tickedRemoteDimensions = dimensions;
        }
    }
    
    public static List<Portal> getPortals(ClientLevel level) {
        if (level != cachedLevel) {
            return Collections.emptyList();
        }
        return cachedPortals;
    }
    
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
