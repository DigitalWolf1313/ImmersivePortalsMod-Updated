package qouteall.imm_ptl.core.block_manipulation;

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
                    client.player.level().dimension(),
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
    
    private static void updateTargetedBlockThroughPortal(
        Vec3 cameraPos,
        Vec3 viewVector,
        ResourceKey<Level> playerDimension,
        double beginDistance,
        double endDistance,
        Portal portal
    ) {
        
        Vec3 from = portal.transformPoint(
            cameraPos.add(viewVector.scale(beginDistance))
        );
        Vec3 to = portal.transformPoint(
            cameraPos.add(viewVector.scale(endDistance))
        );
        
        ClipContext context = new ClipContext(
            from,
            to,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            client.player
        );
        
        ClientLevel world = ClientWorldLoader.getWorld(portal.getDestDim());
        
        remoteHitResult = BlockGetter.traverseBlocks(
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

        // IPSable Intergration: the traverseBlocks lambda above bypasses level.clip, so sub-levels
        // (native ones in the dest world AND foreign straddle projections into it) are
        // invisible to it. Run the overlay-aware clip too; if it hits a ship (plot-coord
        // result) that is frame-closer than the terrain hit, prefer it.
        if (IPSableCompat.isSablePresent&& IPSableCompat.isIPSablePresent) {
            BlockHitResult overlayHit = world.clip(new ClipContext(
                from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
            if (overlayHit != null && overlayHit.getType() == HitResult.Type.BLOCK
                && (Math.abs(overlayHit.getBlockPos().getX()) >= 1_000_000
                    || Math.abs(overlayHit.getBlockPos().getZ()) >= 1_000_000)) {
                double terrainDistSq = hitResultIsMissedOrNull(remoteHitResult)
                    ? Double.MAX_VALUE
                    : from.distanceToSqr(remoteHitResult.getLocation());
                double shipDistSq = IPSableIntegration.frameAwareDistanceSqr(
                    world, from, overlayHit.getLocation());
                if (shipDistSq < terrainDistSq) {
                    remoteHitResult = overlayHit;
                }
            }
        }

        if (remoteHitResult.getLocation().y < world.getMinBuildHeight() + 0.1) {
            remoteHitResult = new BlockHitResult(
                remoteHitResult.getLocation(),
                Direction.DOWN,
                ((BlockHitResult) remoteHitResult).getBlockPos(),
                ((BlockHitResult) remoteHitResult).isInside()
            );
        }
        
        // if an entity is closer than the block/fluid hit, target it instead
        // (this is what allows melee attacks and interactions to reach through a portal)
        EntityHitResult entityHitResult = raytraceEntitiesThroughPortal(world, from, to);
        if (entityHitResult != null) {
            double entityDistSq = from.distanceToSqr(entityHitResult.getLocation());
            double blockDistSq = hitResultIsMissedOrNull(remoteHitResult)
                ? Double.MAX_VALUE
                : from.distanceToSqr(remoteHitResult.getLocation());
            if (entityDistSq < blockDistSq) {
                remoteHitResult = entityHitResult;
            }
        }
        
        if (remoteHitResult != null) {
            if (remoteHitResult instanceof EntityHitResult) {
                client.hitResult = createMissedHitResult(from, to);
                remotePointedDim = portal.getDestDim();
            }
            else if (!world.getBlockState(((BlockHitResult) remoteHitResult).getBlockPos()).isAir()) {
                client.hitResult = createMissedHitResult(from, to);
                remotePointedDim = portal.getDestDim();
            }
        }
        
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
