package qouteall.imm_ptl.core.block_manipulation;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.compat.IPSableCompat;
import qouteall.imm_ptl.core.compat.sable_compatibility.IPSableIntegration;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalPlaceholderBlock;
import qouteall.imm_ptl.core.portal.PortalUtils;
import qouteall.q_misc_util.my_util.RayTraceResult;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class BlockManipulationClient {
    private static final Minecraft client = Minecraft.getInstance();
    
    public static ResourceKey<Level> remotePointedDim;
    public static HitResult remoteHitResult;
    
    public static boolean isPointingToPortal() {
        return remotePointedDim != null;
    }
    
    @Nullable
    public static ClientLevel getRemotePointedWorld() {
        if (remotePointedDim == null) {
            return null;
        }
        return ClientWorldLoader.getWorld(BlockManipulationClient.remotePointedDim);
    }
    
    private static BlockHitResult createMissedHitResult(Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from).normalize();
        
        return BlockHitResult.miss(to, Direction.getNearest(dir.x, dir.y, dir.z), BlockPos.containing(to));
    }
    
    private static boolean hitResultIsMissedOrNull(HitResult bhr) {
        return bhr == null || bhr.getType() == HitResult.Type.MISS;
    }
    
    /**
     * Moves the start of a ray slightly along the ray.
     * It keeps the ray from hitting a portal that lies on the same plane as the exit it came out of.
     * Floating point error would otherwise make it randomly enter such a back-facing portal.
     */
    private static Vec3 movePastExit(Vec3 from, Vec3 to) {
        return from.add(to.subtract(from).normalize().scale(0.001));
    }
    
    /**
     * Returns whether the portal maps the exit point back to the point where the ray entered.
     * Such a portal is the reverse or a flipped twin of the portal that the ray just passed through.
     * Entering it would send the ray back to the previous world.
     */
    private static boolean leadsBackToEntry(
        Portal portal, ResourceKey<Level> previousDim, Vec3 exitPos, Vec3 entryPos
    ) {
        return portal.getDestDim() == previousDim
            && portal.transformPoint(exitPos).distanceToSqr(entryPos) < 0.0001;
    }
    
    public static void updatePointedBlock(float partialTick) {
        if (client.gameMode == null || client.level == null || client.player == null) {
            return;
        }
        
        remotePointedDim = null;
        remoteHitResult = null;
        
        if (!BlockManipulationServer.canDoCrossPortalInteractionEvent.invoker().test(client.player)) {
            return;
        }
        
        Vec3 cameraPos = client.gameRenderer.getMainCamera().getPosition();
        
        double reachDistance = client.player.blockInteractionRange();
        
        PortalUtils.raytracePortalFromEntityView(client.player, partialTick, reachDistance, true, portal1 -> portal1.isInteractableBy(client.player)).ifPresent(pair -> {
            Portal portal = pair.getFirst();
            Vec3 hitPos = pair.getSecond().hitPos();
            double distanceToPortalPointing = hitPos.distanceTo(cameraPos);
            if (distanceToPortalPointing < getCurrentTargetDistance() + 0.2) {
                client.hitResult = createMissedHitResult(cameraPos, hitPos);
                
                updateTargetedBlockThroughPortal(
                    cameraPos,
                    client.player.getViewVector(partialTick),
                    distanceToPortalPointing,
                    reachDistance,
                    portal
                );
            }
        });
    }
    
    private static double getCurrentTargetDistance() {
        Vec3 cameraPos = client.gameRenderer.getMainCamera().getPosition();
        
        if (hitResultIsMissedOrNull(client.hitResult)) {
            return 23333;
        }
        
        if (client.hitResult instanceof BlockHitResult) {
            BlockPos hitPos = ((BlockHitResult) client.hitResult).getBlockPos();
            if (client.level.getBlockState(hitPos).getBlock() == PortalPlaceholderBlock.instance) {
                return 23333;
            }
        }
        
        // Frame-aware: a local hit on a Sable sub-level is in PLOT coordinates; the raw
        // distance (~20M) would let the portal path wrongly steal priority from it.
        return Math.sqrt(IPSableIntegration.frameAwareDistanceSqr(
            client.level, cameraPos, client.hitResult.getLocation()));
    }
    
    /**
     * Traces the view ray through the portal and through any further portals that the ray enters.
     * The final world and hit are stored in {@link #remotePointedDim} and {@link #remoteHitResult}.
     */
    private static void updateTargetedBlockThroughPortal(
        Vec3 cameraPos,
        Vec3 viewVector,
        double beginDistance,
        double endDistance,
        Portal portal
    ) {
        
        Vec3 entryPos = cameraPos.add(viewVector.scale(beginDistance));
        Vec3 exitPos = portal.transformPoint(entryPos);
        ResourceKey<Level> previousDim = client.player.level().dimension();
        
        Vec3 to = portal.transformPoint(
            cameraPos.add(viewVector.scale(endDistance))
        );
        Vec3 from = movePastExit(exitPos, to);
        ClientLevel world = ClientWorldLoader.getWorld(portal.getDestDim());
        
        HitResult hitResult;
        
        for (int layer = 1; ; layer++) {
            BlockHitResult blockHit = raytraceBlocksInWorld(world, from, to);
            EntityHitResult entityHit = raytraceEntitiesThroughPortal(world, from, to);
            
            // An entity that is closer than the block or fluid hit is targeted instead.
            // This allows melee attacks and interactions to reach through a portal.
            if (entityHit != null
                && distanceToHit(world, from, entityHit) < distanceToHit(world, from, blockHit)
            ) {
                hitResult = entityHit;
            }
            else {
                hitResult = blockHit;
            }
            
            if (layer >= BlockManipulationServer.MAX_PORTAL_LAYERS) {
                break;
            }
            
            ResourceKey<Level> returnDim = previousDim;
            Vec3 returnEntryPos = entryPos;
            Vec3 returnExitPos = exitPos;
            Optional<Pair<Portal, RayTraceResult>> nextPortalHit = PortalUtils.raytracePortals(
                world, from, to, true,
                p -> p.isInteractableBy(client.player)
                    && !leadsBackToEntry(p, returnDim, returnExitPos, returnEntryPos)
            );
            if (nextPortalHit.isEmpty()) {
                break;
            }
            
            Portal nextPortal = nextPortalHit.get().getFirst();
            Vec3 portalHitPos = nextPortalHit.get().getSecond().hitPos();
            
            // The small tolerance lets the ray enter a portal that overlaps a block surface.
            if (portalHitPos.distanceTo(from) >= distanceToHit(world, from, hitResult) + 0.0001) {
                break;
            }
            
            Vec3 newFrom = nextPortal.transformPoint(portalHitPos);
            Vec3 newTo = nextPortal.transformPoint(to);
            
            previousDim = world.dimension();
            entryPos = portalHitPos;
            exitPos = newFrom;
            from = movePastExit(newFrom, newTo);
            to = newTo;
            world = ClientWorldLoader.getWorld(nextPortal.getDestDim());
        }
        
        remoteHitResult = hitResult;
        client.crosshairPickEntity = hitResult instanceof EntityHitResult entityHit
            ? entityHit.getEntity()
            : null;
        
        if (hitResult instanceof EntityHitResult
            || !world.getBlockState(((BlockHitResult) hitResult).getBlockPos()).isAir()
        ) {
            client.hitResult = createMissedHitResult(from, to);
            remotePointedDim = world.dimension();
        }
    
    }
    
    /**
     * Returns the distance from the start to the hit location.
     * Returns {@link Double#MAX_VALUE} when there is no hit.
     */
    private static double distanceToHit(ClientLevel world, Vec3 from, @Nullable HitResult hitResult) {
        if (hitResultIsMissedOrNull(hitResult)) {
            return Double.MAX_VALUE;
        }
        
        // Frame-aware: a hit on a Sable sub-level is in PLOT coordinates, so the raw distance is huge.
        return Math.sqrt(IPSableIntegration.frameAwareDistanceSqr(
            world, from, hitResult.getLocation()
        ));
    }
    
    /**
     * Raytraces the blocks and fluids of one world between the two points.
     * Returns a miss result when nothing is hit.
     */
    private static BlockHitResult raytraceBlocksInWorld(ClientLevel world, Vec3 from, Vec3 to) {
        ClipContext context = new ClipContext(
            from,
            to,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            client.player
        );
        
        BlockHitResult blockHit = BlockGetter.traverseBlocks(
            from, to,
            context,
            (rayTraceContext, blockPos) -> {
                BlockState blockState = world.getBlockState(blockPos);
                
                if (blockState.getBlock() == PortalPlaceholderBlock.instance) {
                    return null;
                }
                if (blockState.getBlock() == Blocks.BARRIER) {
                    return null;
                }
                
                FluidState fluidState = world.getFluidState(blockPos);
                Vec3 start = rayTraceContext.getFrom();
                Vec3 end = rayTraceContext.getTo();
                
                //correct the start pos to avoid being considered inside block
                Vec3 correctedStart = start.subtract(end.subtract(start).scale(0.0015));
//                Vec3d correctedStart = start;
                VoxelShape solidShape = rayTraceContext.getBlockShape(blockState, world, blockPos);
                BlockHitResult blockHitResult = world.clipWithInteractionOverride(
                    correctedStart, end, blockPos, solidShape, blockState
                );
                VoxelShape fluidShape = rayTraceContext.getFluidShape(fluidState, world, blockPos);
                BlockHitResult fluidHitResult = fluidShape.clip(start, end, blockPos);
                double d = blockHitResult == null ? Double.MAX_VALUE :
                    rayTraceContext.getFrom().distanceToSqr(blockHitResult.getLocation());
                double e = fluidHitResult == null ? Double.MAX_VALUE :
                    rayTraceContext.getFrom().distanceToSqr(fluidHitResult.getLocation());
                return d <= e ? blockHitResult : fluidHitResult;
            },
            (rayTraceContext) -> {
                Vec3 vec3d = rayTraceContext.getFrom().subtract(rayTraceContext.getTo());
                return BlockHitResult.miss(
                    rayTraceContext.getTo(),
                    Direction.getNearest(vec3d.x, vec3d.y, vec3d.z),
                    BlockPos.containing(rayTraceContext.getTo())
                );
            }
        );
        
        // IPSable integration: traverseBlocks bypasses level.clip, so sub-levels are hidden.
        // Run an overlay-aware clip as well and prefer a ship hit when it is closer than terrain.
        if (IPSableCompat.isSablePresent&& IPSableCompat.isIPSablePresent) {
            BlockHitResult overlayHit = world.clip(new ClipContext(
                from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
            if (overlayHit != null && overlayHit.getType() == HitResult.Type.BLOCK
                && (Math.abs(overlayHit.getBlockPos().getX()) >= 1_000_000
                    || Math.abs(overlayHit.getBlockPos().getZ()) >= 1_000_000)) {
                double terrainDistSq = hitResultIsMissedOrNull(blockHit)
                    ? Double.MAX_VALUE
                    : from.distanceToSqr(blockHit.getLocation());
                double shipDistSq = IPSableIntegration.frameAwareDistanceSqr(
                    world, from, overlayHit.getLocation());
                if (shipDistSq < terrainDistSq) {
                    blockHit = overlayHit;
                }
            }
        }
        
        if (blockHit.getLocation().y < world.getMinBuildHeight() + 0.1) {
            blockHit = new BlockHitResult(
                blockHit.getLocation(),
                Direction.DOWN,
                blockHit.getBlockPos(),
                blockHit.isInside()
            );
        }
        
        return blockHit;
    }
    
    /**
     * Raytraces entities in the destination world along the portal-transformed ray,
     * mirroring the algorithm used by vanilla entity picking (see
     * {@code net.minecraft.world.entity.projectile.ProjectileUtil#getEntityHitResult}),
     * but without needing a "shooter" entity to anchor it (the player entity does not
     * exist in the remote {@link ClientLevel}).
     */
    @Nullable
    private static EntityHitResult raytraceEntitiesThroughPortal(
        ClientLevel world, Vec3 from, Vec3 to
    ) {
        double closestDistSq = from.distanceToSqr(to);
        
        AABB searchBox = new AABB(from, to).inflate(1.0);
        
        List<Entity> candidates = world.getEntities(
            (Entity) null, searchBox,
            entity -> entity.isPickable() && !entity.isSpectator() && !(entity instanceof Portal)
        );
        
        Entity closestEntity = null;
        Vec3 closestHitPos = null;
        
        for (Entity entity : candidates) {
            AABB entityBox = entity.getBoundingBox().inflate(entity.getPickRadius());
            
            if (entityBox.contains(from)) {
                if (closestEntity == null || closestDistSq > 0) {
                    closestEntity = entity;
                    closestHitPos = from;
                    closestDistSq = 0;
                }
                continue;
            }
            
            Optional<Vec3> hit = entityBox.clip(from, to);
            if (hit.isPresent()) {
                double distSq = from.distanceToSqr(hit.get());
                if (distSq < closestDistSq) {
                    closestEntity = entity;
                    closestHitPos = hit.get();
                    closestDistSq = distSq;
                }
            }
        }
        
        if (closestEntity == null) {
            return null;
        }
        
        return new EntityHitResult(closestEntity, closestHitPos);
    }
    
    /**
     * It will not switch the dimension of client player
     */
    public static <T> T withSwitchedContext(
        Supplier<T> func, boolean transformHitResult
    ) {
        Validate.notNull(remoteHitResult);
        
        ClientLevel remoteWorld = getRemotePointedWorld();
        Validate.notNull(remoteWorld);
        
        HitResult effectiveHitResult;
        
        if (transformHitResult && (remoteHitResult instanceof BlockHitResult blockHitResult)) {
            Tuple<BlockHitResult, ResourceKey<Level>> r =
                BlockManipulationServer.getHitResultForPlacing(remoteWorld, blockHitResult);
            effectiveHitResult = r.getA();
            remoteWorld = ClientWorldLoader.getWorld(r.getB());
            Validate.notNull(remoteWorld);
            Validate.notNull(effectiveHitResult);
        }
        else {
            effectiveHitResult = remoteHitResult;
        }
        
        return ClientWorldLoader.withSwitchedWorld(
            remoteWorld, () -> {
                HitResult originalHitResult = client.hitResult;
                client.hitResult = effectiveHitResult;
                try {
                    return func.get();
                }
                finally {
                    client.hitResult = originalHitResult;
                }
            }
        );
    }
    
    @Nullable
    public static String getDebugString() {
        if (remotePointedDim == null) {
            return null;
        }
        if (remoteHitResult instanceof BlockHitResult blockHitResult) {
            return "Point:%s %d %d %d".formatted(
                remotePointedDim.location(),
                blockHitResult.getBlockPos().getX(),
                blockHitResult.getBlockPos().getY(),
                blockHitResult.getBlockPos().getZ()
            );
        }
        else {
            return null;
        }
    }
}
