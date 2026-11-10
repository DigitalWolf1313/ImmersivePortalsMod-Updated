
package qouteall.imm_ptl.core.portal;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import qouteall.q_misc_util.Helper;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes a portal emit block light by keeping invisible light blocks around its plane.
 * The light blocks are only placed into air and are tracked by position, so they can be removed later.
 * The tracked positions are saved with the portal, so leftover light blocks are cleaned up after a restart.
 * The portal emits either one uniform light level or an explicit light level for each cell.
 * The explicit levels take priority and are filled by copying the light around another portal.
 * The explicit cells are stored in the portal's local frame, so they follow the portal when it moves.
 * A moved portal updates its light blocks in the same tick.
 * Only the server runs this logic.
 */
public class PortalLightEmitter {
    /**
     * A light level at a point given in the portal's local frame.
     * The coordinates are measured along the width axis, the height axis and the normal.
     */
    private record LightCell(double u, double v, double w, int level) {}
    
    /**
     * The upper bound of light blocks that one portal may own.
     */
    private static final int MAX_LIGHT_BLOCKS = 450;
    
    /**
     * The upper bound of sample points that are scanned when copying light.
     */
    private static final int MAX_SCAN_SAMPLES = 20000;
    
    /**
     * How far the light blocks sit from the portal plane along its normal.
     */
    private static final double FACE_OFFSET = 0.6;
    
    /**
     * How often the light blocks are verified and repaired, in ticks.
     */
    private static final int UPDATE_INTERVAL = 20;
    
    private int lightLevel = 0;
    
    /**
     * The explicit light cells.
     * They are used instead of the uniform light level when the list is not empty.
     */
    private final List<LightCell> cells = new ArrayList<>();
    
    private final LongSet ownedPositions = new LongOpenHashSet();
    
    private int tickCounter = 0;
    
    private boolean dirty = false;
    
    private Vec3 lastOrigin;
    
    private Vec3 lastAxisW;
    
    private Vec3 lastAxisH;
    
    private double lastWidth;
    
    private double lastHeight;
    
    public int getLightLevel() {
        return lightLevel;
    }
    
    /**
     * Sets the uniform light level and discards the explicit cell levels.
     */
    public void setLightLevel(int newLevel) {
        newLevel = Mth.clamp(newLevel, 0, 15);
        if (newLevel != lightLevel || !cells.isEmpty()) {
            lightLevel = newLevel;
            cells.clear();
            dirty = true;
        }
    }
    
    /**
     * Sets the explicit light cells and clears the uniform light level.
     */
    private void setCells(List<LightCell> newCells) {
        if (lightLevel == 0 && cells.equals(newCells)) {
            return;
        }
        
        lightLevel = 0;
        cells.clear();
        cells.addAll(newCells);
        dirty = true;
    }
    
    /**
     * A present uniform light level takes priority over the explicit cell levels.
     * This lets a merged tag with a new uniform level replace the cell levels.
     */
    public void readFromTag(CompoundTag tag) {
        if (tag.contains("lightLevel")) {
            setLightLevel(tag.getInt("lightLevel"));
        }
        else {
            long[] local = tag.getLongArray("lightCellLocal");
            int[] levels = tag.getIntArray("lightCellLevels");
            
            List<LightCell> newCells = new ArrayList<>();
            for (int i = 0; i < Math.min(local.length / 3, levels.length); i++) {
                newCells.add(new LightCell(
                    Double.longBitsToDouble(local[i * 3]),
                    Double.longBitsToDouble(local[i * 3 + 1]),
                    Double.longBitsToDouble(local[i * 3 + 2]),
                    levels[i]
                ));
            }
            setCells(newCells);
        }
        
        ownedPositions.clear();
        for (long pos : tag.getLongArray("lightBlockPositions")) {
            ownedPositions.add(pos);
        }
    }
    
    public void writeToTag(CompoundTag tag) {
        if (lightLevel != 0) {
            tag.putInt("lightLevel", lightLevel);
        }
        
        if (!cells.isEmpty()) {
            long[] local = new long[cells.size() * 3];
            int[] levels = new int[cells.size()];
            for (int i = 0; i < cells.size(); i++) {
                LightCell cell = cells.get(i);
                local[i * 3] = Double.doubleToLongBits(cell.u());
                local[i * 3 + 1] = Double.doubleToLongBits(cell.v());
                local[i * 3 + 2] = Double.doubleToLongBits(cell.w());
                levels[i] = cell.level();
            }
            tag.putLongArray("lightCellLocal", local);
            tag.putIntArray("lightCellLevels", levels);
        }
        
        if (!ownedPositions.isEmpty()) {
            tag.putLongArray("lightBlockPositions", ownedPositions.toLongArray());
        }
    }
    
    public void tick(Portal portal) {
        if (lightLevel == 0 && cells.isEmpty() && ownedPositions.isEmpty()) {
            return;
        }
        
        if (!(portal.level() instanceof ServerLevel world)) {
            return;
        }
        
        tickCounter++;
        boolean moved = hasPoseChanged(portal);
        if (!dirty && !moved && tickCounter % UPDATE_INTERVAL != 0) {
            return;
        }
        dirty = false;
        
        update(portal, world);
    }
    
    /**
     * Returns whether the portal's placement or size differs from the previous call.
     * Dragging a portal changes these values every tick.
     */
    private boolean hasPoseChanged(Portal portal) {
        Vec3 origin = portal.getOriginPos();
        if (origin.equals(lastOrigin) &&
            portal.axisW.equals(lastAxisW) &&
            portal.axisH.equals(lastAxisH) &&
            portal.width == lastWidth &&
            portal.height == lastHeight
        ) {
            return false;
        }
        
        lastOrigin = origin;
        lastAxisW = portal.axisW;
        lastAxisH = portal.axisH;
        lastWidth = portal.width;
        lastHeight = portal.height;
        return true;
    }
    
    public void onRemoved(Portal portal, Entity.RemovalReason reason) {
        // Unloading the portal must keep the light blocks because they are saved with the chunk.
        if (!reason.shouldDestroy()) {
            return;
        }
        
        if (!(portal.level() instanceof ServerLevel world)) {
            return;
        }
        
        int removedCount = 0;
        LongIterator iterator = ownedPositions.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = BlockPos.of(iterator.nextLong());
            if (world.hasChunkAt(pos) && world.getBlockState(pos).is(Blocks.LIGHT)) {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                removedCount++;
            }
        }
        ownedPositions.clear();
    }
    
    /**
     * Copies the block light around the source portal onto the matching cells around the target portal.
     * Each light block takes the level of the source cell that it matches, so the light continues through the portal.
     * The light blocks of the source portal are included in the reading.
     * Cells without block light are skipped and do not count towards the light block limit.
     * The cells are stored relative to the target portal, so they follow it when it or its destination moves.
     * Returns the number of cells that receive light.
     */
    public static int copyLightThroughPortal(Portal source, Portal target) {
        double step = chooseStep(source, MAX_SCAN_SAMPLES);
        Long2ObjectOpenHashMap<LightCell> found = readLightCells(source, target, step);
        while (found.size() > MAX_LIGHT_BLOCKS) {
            step += Math.max(1, Math.floor(step / 4));
            found = readLightCells(source, target, step);
        }
        
        target.lightEmitter.setCells(new ArrayList<>(found.values()));
        return found.size();
    }
    
    /**
     * Returns the lit cells keyed by the block position that they occupy on the target side.
     * Each cell is expressed in the target portal's local frame.
     */
    private static Long2ObjectOpenHashMap<LightCell> readLightCells(Portal source, Portal target, double step) {
        Level world = source.level();
        Vec3 offset = source.getNormal().scale(FACE_OFFSET);
        Vec3 targetOrigin = target.getOriginPos();
        Vec3 targetNormal = target.getNormal();
        
        Long2ObjectOpenHashMap<LightCell> found = new Long2ObjectOpenHashMap<>();
        for (Vec3 point : computeSamplePoints(source, step)) {
            for (Vec3 sourcePoint : pointsAcrossPlane(point, offset)) {
                BlockPos sourcePos = BlockPos.containing(sourcePoint);
                if (!world.hasChunkAt(sourcePos)) {
                    continue;
                }
                
                int level = world.getBrightness(LightLayer.BLOCK, sourcePos);
                if (level == 0) {
                    continue;
                }
                
                Vec3 targetPoint = source.transformPoint(sourcePoint);
                long targetPos = BlockPos.containing(targetPoint).asLong();
                LightCell existing = found.get(targetPos);
                if (existing != null && existing.level() >= level) {
                    continue;
                }
                
                Vec3 local = targetPoint.subtract(targetOrigin);
                found.put(targetPos, new LightCell(
                    local.dot(target.axisW), local.dot(target.axisH), local.dot(targetNormal), level
                ));
            }
        }
        return found;
    }
    
    private void update(Portal portal, ServerLevel world) {
        Long2IntMap desired = computeDesiredLevels(portal);
        
        int removedCount = 0;
        int placedCount = 0;
        
        // Positions in unloaded chunks stay owned until the chunk is loaded again.
        LongIterator ownedIterator = ownedPositions.iterator();
        while (ownedIterator.hasNext()) {
            long packedPos = ownedIterator.nextLong();
            if (desired.containsKey(packedPos)) {
                continue;
            }
            
            BlockPos pos = BlockPos.of(packedPos);
            if (!world.hasChunkAt(pos)) {
                continue;
            }
            
            if (world.getBlockState(pos).is(Blocks.LIGHT)) {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                removedCount++;
            }
            ownedIterator.remove();
        }
        
        for (Long2IntMap.Entry entry : desired.long2IntEntrySet()) {
            long packedPos = entry.getLongKey();
            BlockPos pos = BlockPos.of(packedPos);
            if (!world.hasChunkAt(pos)) {
                continue;
            }
            
            BlockState lightState = Blocks.LIGHT.defaultBlockState()
                .setValue(LightBlock.LEVEL, entry.getIntValue());
            BlockState currentState = world.getBlockState(pos);
            
            if (ownedPositions.contains(packedPos)) {
                if (!currentState.is(Blocks.LIGHT)) {
                    // Something else replaced the light block, so the position is given up.
                    ownedPositions.remove(packedPos);
                }
                else if (currentState != lightState) {
                    world.setBlock(pos, lightState, Block.UPDATE_CLIENTS);
                    placedCount++;
                }
            }
            else if (currentState.isAir() && !currentState.is(Blocks.LIGHT)) {
                // Light blocks of other portals are skipped, so the two faces of a bi-way portal do not fight.
                world.setBlock(pos, lightState, Block.UPDATE_CLIENTS);
                ownedPositions.add(packedPos);
                placedCount++;
            }
        }
    }
    
    private Long2IntMap computeDesiredLevels(Portal portal) {
        Long2IntOpenHashMap result = new Long2IntOpenHashMap();
        
        if (!cells.isEmpty()) {
            Vec3 origin = portal.getOriginPos();
            Vec3 normal = portal.getNormal();
            for (LightCell cell : cells) {
                Vec3 point = origin
                    .add(portal.axisW.scale(cell.u()))
                    .add(portal.axisH.scale(cell.v()))
                    .add(normal.scale(cell.w()));
                long packedPos = BlockPos.containing(point).asLong();
                result.put(packedPos, Math.max(result.get(packedPos), cell.level()));
            }
        }
        else if (lightLevel != 0) {
            LongIterator iterator = computeDesiredPositions(portal).iterator();
            while (iterator.hasNext()) {
                result.put(iterator.nextLong(), lightLevel);
            }
        }
        
        return result;
    }
    
    /**
     * Returns the cells around each sample point of the portal.
     */
    private static LongSet computeDesiredPositions(Portal portal) {
        Vec3 offset = portal.getNormal().scale(FACE_OFFSET);
        
        LongSet result = new LongOpenHashSet();
        for (Vec3 point : computeSamplePoints(portal, chooseStep(portal, MAX_LIGHT_BLOCKS / 3))) {
            for (Vec3 cellPoint : pointsAcrossPlane(point, offset)) {
                result.add(BlockPos.containing(cellPoint).asLong());
            }
        }
        return result;
    }
    
    /**
     * Returns the points that stand for the cells on both sides of the portal plane.
     * The cell containing the plane point is only added when it differs from both side cells.
     * A point on a block boundary would otherwise fall into either neighbor cell depending on rounding.
     */
    private static List<Vec3> pointsAcrossPlane(Vec3 point, Vec3 offset) {
        Vec3 front = point.add(offset);
        Vec3 back = point.subtract(offset);
        
        List<Vec3> result = new ArrayList<>(3);
        result.add(front);
        result.add(back);
        
        BlockPos centerPos = BlockPos.containing(point);
        if (!centerPos.equals(BlockPos.containing(front)) && !centerPos.equals(BlockPos.containing(back))) {
            result.add(point);
        }
        return result;
    }
    
    /**
     * Returns a grid spacing in whole blocks whose sample count stays within the limit.
     */
    private static double chooseStep(Portal portal, long maxSamples) {
        double step = 1;
        while (countSamples(portal.width, step) * countSamples(portal.height, step) > maxSamples) {
            step += Math.max(1, Math.floor(step / 4));
        }
        return step;
    }
    
    private static long countSamples(double size, double step) {
        return Math.max(1, (long) Math.ceil(size / step));
    }
    
    /**
     * Samples the portal rectangle on a grid with the given spacing.
     * The portal's special shape is ignored and the whole rectangle is used.
     */
    private static List<Vec3> computeSamplePoints(Portal portal, double step) {
        Vec3 origin = portal.getOriginPos();
        Vec3 axisW = portal.axisW;
        Vec3 axisH = portal.axisH;
        
        int countW = (int) countSamples(portal.width, step);
        int countH = (int) countSamples(portal.height, step);
        
        List<Vec3> result = new ArrayList<>(countW * countH);
        for (int i = 0; i < countW; i++) {
            double u = ((i + 0.5) / countW - 0.5) * portal.width;
            for (int j = 0; j < countH; j++) {
                double v = ((j + 0.5) / countH - 0.5) * portal.height;
                result.add(origin.add(axisW.scale(u)).add(axisH.scale(v)));
            }
        }
        return result;
    }
}
