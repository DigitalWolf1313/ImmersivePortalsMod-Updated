package qouteall.imm_ptl.core.compat.sable_compatibility;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import qouteall.imm_ptl.core.compat.IPSableCompat;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Soft-compat with Sable (server-safe). Sable stores sublevel blocks/entities in a far-out
 * plot grid; ImmPtl's normal chunk/entity tracking does not cover those physical positions.
 *
 * <p>This class contains <strong>only</strong> server-safe methods that do not reference any
 * client-only Minecraft classes, so it can be loaded on a dedicated server without crashing.
 *
 * <p>Sable is a {@code modApi} compile dependency but optional at runtime — always guard
 * with {@link qouteall.imm_ptl.core.compat.IPSableCompat#isSablePresent} before calling
 * into Sable types so dedicated servers / clients without Sable do not resolve those classes.
 */
public class SableInterface {
    
    /**
     * If {@code chunkPos} is a loaded Sable plot chunk, returns the players Sable considers
     * tracking that sublevel. Returns {@code null} when Sable is absent or the chunk is not
     * a plot chunk (caller should use ImmPtl tracking).
     */
    public static @Nullable List<ServerPlayer> getPlayersTrackingPlotChunk(
        final Level level, final ChunkPos chunkPos
    ) {
        
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        
        final LevelPlot plot = container.getPlot(chunkPos);
        if (plot == null) {
            return null;
        }
        
        return container.getPlayersTracking(chunkPos);
    }
    
    /**
     * Whether the chunk is inside a loaded Sable plot (far plot-grid storage).
     */
    public static boolean isSablePlotChunk(final Level level, final ChunkPos chunkPos) {
        
        final SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return false;
        }
        
        return container.getPlot(chunkPos) != null;
    }
    
    /**
     * Whether the chunk is inside a loaded Sable plot (far plot-grid storage).
     * Convenience overload for long-based chunk positions.
     */
    public static boolean isSablePlotChunk(final Level level, final long chunkPosLong) {
        return isSablePlotChunk(level, new ChunkPos(chunkPosLong));
    }

    /**
     * Whether the entity is near a Sable sublevel that could collide with it
     * (standing on it, locked to it, or horizontally touching it).
     *
     * <p>Sable only sets {@code sable$getTrackingSubLevel()} when the entity is
     * <em>standing on</em> a sublevel (vertical collision below). When the entity
     * touches a sublevel from the side, Sable instead records the collision in
     * {@code CollisionInfo.subLevelHorizontalCollision} / {@code horizontalCollision}.
     * This helper checks both so the original collision chain (which includes Sable's
     * sublevel collision redirect on {@link Entity#move}) is not bypassed when portal
     * collision is active. Returns {@code false} when Sable is absent.
     */
    public static boolean isEntityInteractingWithSableSubLevel(final Entity entity) {
        if (!IPSableCompat.isSablePresent) {
            return false;
        }
        if (entity instanceof EntityMovementExtension ext) {
            if (ext.sable$getTrackingSubLevel() != null) {
                return true;
            }
            final SubLevelEntityCollision.CollisionInfo collisionInfo = ext.sable$getCollisionInfo();
            // stale from the last Entity.collide() call. If the entity has recently
            // collided with a sublevel from any direction, run the original chain.
            return collisionInfo != null && (
                collisionInfo.subLevelHorizontalCollision ||
                    collisionInfo.horizontalCollision ||
                    collisionInfo.verticalCollision
            );
        }
        return false;
    }

}