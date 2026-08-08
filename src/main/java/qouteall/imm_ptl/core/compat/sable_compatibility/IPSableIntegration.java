package qouteall.imm_ptl.core.compat.sable_compatibility;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.compat.IPSableCompat;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * All reflection-based calls into IPSable classes. IPSable is an optional add-on that
 * cannot be a compile dependency, so every interaction with it must go through reflection.
 * Otherwise some features just wouldn't work
 *
 * <p>This class centralizes all {@code Class.forName} + {@code getMethod} + {@code invoke}
 * calls so the rest of the codebase only sees plain static methods. Cached {@link Method}
 * handles are resolved once on first use to avoid repeated reflection overhead.
 */
public class IPSableIntegration {

    // Cached reflective handles. Resolved once on first use when IPSable is present,
    // avoiding repeated Class.forName + getMethod overhead (especially important for
    // frameAwareDistanceSqr which is called every client tick).
    private static volatile @Nullable Method plotAwareLevelMethod;
    private static volatile @Nullable Method effectivePortalGenLevelMethod;
    private static volatile @Nullable Method shipFrameWorldCenterMethod;
    private static volatile @Nullable Method mapShipFramePortalPoseMethod;
    private static volatile @Nullable Method anchorShipFramePortalMethod;
    private static volatile @Nullable Method frameAwareDistanceSqrMethod;
    private static volatile @Nullable Method registerIplShipPortalCommandMethod;
    private static volatile @Nullable Method hasChunkAtFrameAwareMethod;
    private static volatile @Nullable Method onPlayerTeleportedMethod;
    private static volatile @Nullable Method onLocalPlayerTeleportedMethod;

    /**
     * Whether the IPSable add-on is present on the classpath.
     * Uses reflection to check for IPSable... There is no other way to check.
     */
    public static boolean isIPSablePresent() {
        try {
            Class.forName("ipl.sable.SableBridge");
            return true;
        }
        catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static @Nullable Level plotAwareLevel(final Level context, final @Nullable BlockPos pos) {
        if (!IPSableCompat.isIPSablePresent) {
            return context;
        }
        Method m = plotAwareLevelMethod;
        if (m == null) {
            try {
                Class<?> clazz = Class.forName("ipl.sable.SableBridge");
                m = clazz.getMethod("plotAwareLevel", Level.class, BlockPos.class);
                plotAwareLevelMethod = m;
            }
            catch (Throwable e) {
                Helper.LOGGER.error("Failed to resolve SableBridge.plotAwareLevel", e);
                return context;
            }
        }
        try {
            return (Level) m.invoke(null, context, pos);
        }
        catch (Throwable e) {
            Helper.LOGGER.error("Failed to call SableBridge.plotAwareLevel", e);
            return context;
        }
    }

    public static ServerLevel effectivePortalGenLevel(final ServerLevel world, final BlockPos pos) {
        if (!IPSableCompat.isIPSablePresent) {
            return world;
        }
        Method m = effectivePortalGenLevelMethod;
        if (m == null) {
            try {
                Class<?> clazz = Class.forName("ipl.sable.SableBridge");
                m = clazz.getMethod("effectivePortalGenLevel", ServerLevel.class, BlockPos.class);
                effectivePortalGenLevelMethod = m;
            }
            catch (Throwable e) {
                Helper.LOGGER.error("Failed to resolve SableBridge.effectivePortalGenLevel", e);
                return world;
            }
        }
        try {
            return (ServerLevel) m.invoke(null, world, pos);
        }
        catch (Throwable e) {
            Helper.LOGGER.error("Failed to call SableBridge.effectivePortalGenLevel", e);
            return world;
        }
    }

    public static @Nullable Vec3 shipFrameWorldCenter(final ServerLevel world, final BlockPos pos) {
        if (!IPSableCompat.isIPSablePresent) {
            return null;
        }
        Method m = shipFrameWorldCenterMethod;
        if (m == null) {
            try {
                Class<?> clazz = Class.forName("ipl.sable.SableBridge");
                m = clazz.getMethod("shipFrameWorldCenter", ServerLevel.class, BlockPos.class);
                shipFrameWorldCenterMethod = m;
            }
            catch (Throwable e) {
                Helper.LOGGER.error("Failed to resolve SableBridge.shipFrameWorldCenter", e);
                return null;
            }
        }
        try {
            return (Vec3) m.invoke(null, world, pos);
        }
        catch (Throwable e) {
            Helper.LOGGER.error("Failed to call SableBridge.shipFrameWorldCenter", e);
            return null;
        }
    }

    public static void mapShipFramePortalPose(final Portal portal) {
        if (!IPSableCompat.isIPSablePresent) {
            return;
        }
        Method m = mapShipFramePortalPoseMethod;
        if (m == null) {
            try {
                Class<?> clazz = Class.forName("ipl.sable.SableBridge");
                m = clazz.getMethod("mapShipFramePortalPose", Portal.class);
                mapShipFramePortalPoseMethod = m;
            }
            catch (Throwable e) {
                Helper.LOGGER.error("Failed to resolve SableBridge.mapShipFramePortalPose", e);
                return;
            }
        }
        try {
            m.invoke(null, portal);
        }
        catch (Throwable e) {
            Helper.LOGGER.error("Failed to call SableBridge.mapShipFramePortalPose", e);
        }
    }

    public static void anchorShipFramePortal(final Portal portal, final BlockPos shapeAnchor) {
        if (!IPSableCompat.isIPSablePresent) {
            return;
        }
        Method m = anchorShipFramePortalMethod;
        if (m == null) {
            try {
                Class<?> clazz = Class.forName("ipl.sable.SableBridge");
                m = clazz.getMethod("anchorShipFramePortal", Portal.class, BlockPos.class);
                anchorShipFramePortalMethod = m;
            }
            catch (Throwable e) {
                Helper.LOGGER.error("Failed to resolve SableBridge.anchorShipFramePortal", e);
                return;
            }
        }
        try {
            m.invoke(null, portal, shapeAnchor);
        }
        catch (Throwable e) {
            Helper.LOGGER.error("Failed to call SableBridge.anchorShipFramePortal", e);
        }
    }

    public static double frameAwareDistanceSqr(final Level level, final Vec3 a, final Vec3 b) {
        if (!IPSableCompat.isIPSablePresent) {
            return a.distanceToSqr(b);
        }
        Method m = frameAwareDistanceSqrMethod;
        if (m == null) {
            try {
                Class<?> clazz = Class.forName("ipl.sable.SableBridge");
                m = clazz.getMethod("frameAwareDistanceSqr", Level.class, Vec3.class, Vec3.class);
                frameAwareDistanceSqrMethod = m;
            }
            catch (Throwable e) {
                Helper.LOGGER.error("Failed to resolve SableBridge.frameAwareDistanceSqr", e);
                return a.distanceToSqr(b);
            }
        }
        try {
            return (double) m.invoke(null, level, a, b);
        }
        catch (Throwable e) {
            Helper.LOGGER.error("Failed to call SableBridge.frameAwareDistanceSqr", e);
            return a.distanceToSqr(b);
        }
    }

    public static void registerIplShipPortalCommand(
        final CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        if (!IPSableCompat.isIPSablePresent) {
            return;
        }
        Method m = registerIplShipPortalCommandMethod;
        if (m == null) {
            try {
                Class<?> clazz = Class.forName("ipl.sable.transit.IplShipPortalCommand");
                m = clazz.getMethod("register", CommandDispatcher.class);
                registerIplShipPortalCommandMethod = m;
            }
            catch (Throwable e) {
                Helper.LOGGER.error("Failed to resolve IplShipPortalCommand.register", e);
                return;
            }
        }
        try {
            m.invoke(null, dispatcher);
        }
        catch (Throwable e) {
            Helper.LOGGER.error("Failed to register IplShipPortalCommand", e);
        }
    }

    public static boolean hasChunkAtFrameAware(
        final ServerLevel world, final BlockPos pos
    ) {
        if (!IPSableCompat.isIPSablePresent) {
            return world.isLoaded(pos);
        }
        Method m = hasChunkAtFrameAwareMethod;
        if (m == null) {
            try {
                Class<?> clazz = Class.forName("ipl.sable.SableBridge");
                m = clazz.getMethod("hasChunkAtFrameAware", ServerLevel.class, BlockPos.class);
                hasChunkAtFrameAwareMethod = m;
            }
            catch (Throwable e) {
                Helper.LOGGER.error("Failed to resolve SableBridge.hasChunkAtFrameAware", e);
                return world.isLoaded(pos);
            }
        }
        try {
            return (boolean) m.invoke(null, world, pos);
        }
        catch (Throwable e) {
            return world.isLoaded(pos);
        }
    }

    public static void onPlayerTeleported(final ServerPlayer player, final Portal portal) {
        if (!IPSableCompat.isIPSablePresent) {
            return;
        }
        Method m = onPlayerTeleportedMethod;
        if (m == null) {
            try {
                Class<?> clazz = Class.forName("ipl.sable.transit.IplGrabChain");
                m = clazz.getMethod("onPlayerTeleported", ServerPlayer.class, Portal.class);
                onPlayerTeleportedMethod = m;
            }
            catch (Throwable e) {
                Helper.LOGGER.error("Failed to resolve IplGrabChain.onPlayerTeleported", e);
                return;
            }
        }
        try {
            m.invoke(null, player, portal);
        }
        catch (Throwable e) {
            Helper.LOGGER.error("Failed to call IplGrabChain.onPlayerTeleported", e);
        }
    }

    public static void onLocalPlayerTeleported(final Portal portal) {
        if (!IPSableCompat.isIPSablePresent) {
            return;
        }
        Method m = onLocalPlayerTeleportedMethod;
        if (m == null) {
            try {
                Class<?> clazz = Class.forName("ipl.sable.client.IplGrabChainClient");
                m = clazz.getMethod("onLocalPlayerTeleported", Portal.class);
                onLocalPlayerTeleportedMethod = m;
            }
            catch (Throwable e) {
                Helper.LOGGER.error("Failed to resolve IplGrabChainClient.onLocalPlayerTeleported", e);
                return;
            }
        }
        try {
            m.invoke(null, portal);
        }
        catch (Throwable e) {
            Helper.LOGGER.error("Failed to call IplGrabChainClient.onLocalPlayerTeleported", e);
        }
    }

}