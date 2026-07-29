package qouteall.imm_ptl.core.compat.sable_compatibility;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import qouteall.imm_ptl.core.compat.IPSableCompat;

import org.jetbrains.annotations.Nullable;

/**
 * Client-only Sable compat methods. Separated from {@link SableInterface} so that
 * the server-safe class does not reference any client-only Minecraft classes, which
 * would cause {@link NoClassDefFoundError} on a dedicated server.
 *
 * <p>All methods in this class are annotated {@link Environment#CLIENT} and will be
 * stripped on dedicated server environments by Fabric's client-only class filtering.
 */
@Environment(EnvType.CLIENT)
public class SableClientInterface {

    // Workaround to prevent Sable's custom camera modes from resetting to first person.
    public static void runWithTemporaryFirstPerson(final Runnable task) {
        runWithTemporaryFirstPerson(Minecraft.getInstance(), task);
    }

    public static void runWithTemporaryFirstPerson(final Minecraft client, final Runnable task) {
        final CameraType originalCameraType = client.options.getCameraType();
        if (!isSableCustomCameraType(originalCameraType)) {
            task.run();
            return;
        }

        client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        try {
            task.run();
        }
        finally {
            client.options.setCameraType(originalCameraType);
        }
    }

    public static boolean isSableCustomCameraType(final CameraType cameraType) {
        return cameraType == SableCameraTypes.SUB_LEVEL_VIEW
            || cameraType == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED;
    }

    /**
     * Whether the given plot-grid coordinates are within a loaded Sable plot boundary.
     *
     * @param level the client level to check
     * @param x     the plot-grid X coordinate
     * @param z     the plot-grid Z coordinate
     * @return {@code true} if the coordinates fall inside a loaded Sable plot
     */
    public static boolean isSablePlotBound(ClientLevel level, int x, int z) {
        SubLevelContainer container = SubLevelContainer.getContainer((Level) level);
        return container != null && container.inBounds(x, z);
    }

    /**
     * Ticks the Sable plot container of a client world. Sable only ticks the plot container
     * of {@code Minecraft.getInstance().level} (the main client level) in its own mixin, so
     * when ImmPtl renders and ticks a remote dimension through a portal the sublevels in that
     * dimension would otherwise never tick (no interpolation, physics, or block entity updates)
     * and appear frozen. Calling this for each remote world keeps them in sync.
     *
     * <p>No-op when Sable is absent or the world has no plot container.
     */
    public static void tickRemotePlotContainer(final ClientLevel level) {
        if (!IPSableCompat.isSablePresent) {
            return;
        }

        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        container.tick();
    }

}