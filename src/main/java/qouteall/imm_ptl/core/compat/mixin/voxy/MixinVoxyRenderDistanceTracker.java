package qouteall.imm_ptl.core.compat.mixin.voxy;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.IPVoxyCompat;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.q_misc_util.Helper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * Keeps Voxy's requested LoD nodes alive for every view rendered in a frame.
 * Each view is keyed by its full portal path, like in {@link MixinVoxyViewportSelector},
 * and has its own ring of desired nodes.
 * Nodes shared between overlapping views are reference counted,
 * so a node is only unloaded once every view has released it.
 * Voxy's own tracker is never used, because it follows a single camera
 * and adds nodes without reference counts.
 */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.rendering.RenderDistanceTracker", remap = false)
public abstract class MixinVoxyRenderDistanceTracker {
    @Shadow private LongConsumer addTopLevelNode;
    @Shadow private LongConsumer removeTopLevelNode;
    @Shadow private int processRate;
    @Shadow private int minSec;
    @Shadow private int maxSec;
    
    // The views keyed by full portal path, like in MixinVoxyViewportSelector.
    // The outer camera uses the empty path that getPortalPath() returns outside portal rendering.
    // Mixin's field initializer merging is unreliable for @Pseudo mixins,
    // so iPortals$onConstructed initializes this field.
    @Unique
    private Map<List<Portal>, iPortals$View> iPortals$views;
    
    // Counts the views that desire each node,
    // so a node is only unloaded once every view has released it.
    // It is initialized in iPortals$onConstructed for the same reason as iPortals$views.
    @Unique
    private Long2IntOpenHashMap iPortals$nodeReferences;
    
    @Unique
    private int iPortals$renderDistance;
    
    // A view that has not been rendered for this many nanoseconds (30 seconds) is discarded.
    @Unique
    private static final long iPortals$DISCARD_GRACE_NANOS = 30_000_000_000L;
    
    // Voxy's constructor starts the render distance at 2, and this mirrors that.
    // It also creates iPortals$views and iPortals$nodeReferences.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void iPortals$onConstructed(
        int rate, int minSec, int maxSec,
        LongConsumer addTopLevelNode, LongConsumer removeTopLevelNode,
        CallbackInfo ci
    ) {
        this.iPortals$renderDistance = 2;
        this.iPortals$views = new HashMap<>();
        this.iPortals$nodeReferences = new Long2IntOpenHashMap();
    }
    
    @Inject(method = "setRenderDistance", at = @At("HEAD"), cancellable = true)
    private void iPortals$setRenderDistance(int renderDistance, CallbackInfo ci) {
        if (renderDistance != this.iPortals$renderDistance) {
            this.iPortals$renderDistance = renderDistance;
            for (iPortals$View view : this.iPortals$views.values()) {
                view.rebuild(renderDistance);
            }
        }
        ci.cancel();
    }
    
    @Inject(method = "setCenterAndProcess", at = @At("HEAD"), cancellable = true)
    private void iPortals$setCenterAndProcess(double x, double z, CallbackInfoReturnable<Boolean> cir) {
        // The path is empty when rendering the outer world.
        List<Portal> path = PortalRendering.getPortalPath();
        // Cross-dimension portal views are tracked here too.
        // Voxy's own ring is never used, since it adds nodes without reference counts
        // and would collide with the nodes that these views add.
        long now = System.nanoTime();
        
        if (path.isEmpty()) {
            // This must run before discardInactiveViews.
            // It can hand a view off to a new key and refresh its lastSeenNanos,
            // which the discard check must see.
            this.iPortals$applyPendingRenderDistanceSwap(now);
        }
        // This also runs for portal views,
        // since a dimension's renderer can go a long time without being the outer camera.
        this.iPortals$discardInactiveViews(now);
        
        iPortals$View view = this.iPortals$views.computeIfAbsent(path, ignored -> new iPortals$View());
        view.lastSeenNanos = now;
        view.updateCenter(x, z, this.iPortals$renderDistance);
        
        // Only this view's own backlog is drained here.
        // Voxy calls this once per view per frame, so each view gets its full per-frame budget.
        cir.setReturnValue(view.drain(this.processRate, this::iPortals$apply));
    }
    
    @Unique
    private static final List<Portal> iPortals$OUTER_PATH = List.of();
    
    /**
     * Consumes any pending same-dimension teleport notification
     * and swaps the outer and portal-destination views by identity,
     * so neither has to rebuild its nodes after teleporting.
     */
    @Unique
    private void iPortals$applyPendingRenderDistanceSwap(long now) {
        Portal swapPortal = IPVoxyCompat.consumeVoxyRenderDistanceSwap();
        if (swapPortal == null) {
            return;
        }
        
        // This view tracks the portal's destination, which is roughly where the player now stands.
        // It is null if the destination was never rendered as its own view.
        iPortals$View promoted = this.iPortals$views.remove(List.of(swapPortal));
        
        // This view tracks the area the player just left and is still keyed under the outer path.
        iPortals$View previousOuter = this.iPortals$views.get(iPortals$OUTER_PATH);
        
        if (promoted != null) {
            this.iPortals$views.put(iPortals$OUTER_PATH, promoted);
            promoted.lastSeenNanos = now;
        }
        // When no view was promoted, the outer view keeps its object
        // and rebuilds normally in updateCenter below.
        
        if (previousOuter != null) {
            Portal reversePortal = PortalManipulation.findReversePortal(swapPortal);
            if (reversePortal != null) {
                // The vacated outer view goes to the reverse portal,
                // so looking back right after teleporting reuses its resident nodes.
                List<Portal> reversePath = List.of(reversePortal);
                iPortals$View displaced = this.iPortals$views.put(reversePath, previousOuter);
                previousOuter.lastSeenNanos = now;
                if (displaced != null && displaced != previousOuter) {
                    // This view is stale from an earlier look-back through the same reverse portal.
                    displaced.clear();
                    displaced.drainAll(this::iPortals$apply);
                }
            }
            else if (promoted != null) {
                // A one-way portal has no reverse portal to receive the vacated view,
                // and the outer path's entry was just overwritten with the promoted view,
                // so the orphaned view is released.
                previousOuter.clear();
                previousOuter.drainAll(this::iPortals$apply);
            }
            // With a one-way portal and no promoted view, nothing replaced previousOuter,
            // so it is still the live outer view and is left alone.
        }
    }
    
    @Unique
    private void iPortals$discardInactiveViews(long now) {
        var iterator = this.iPortals$views.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            
            // The outer view is never discarded.
            if (entry.getKey().isEmpty()) {
                continue;
            }
            
            iPortals$View view = entry.getValue();
            if (now - view.lastSeenNanos <= iPortals$DISCARD_GRACE_NANOS) {
                continue;
            }
            // The portal has not been rendered within the grace window,
            // so everything it keeps alive is released.
            // The release is not budgeted, since anything left pending
            // would leak its retained nodes once the view is dropped.
            view.clear();
            view.drainAll(this::iPortals$apply);
            iterator.remove();
        }
    }
    
    @Unique
    private void iPortals$apply(int x, int z, int delta) {
        for (int y = this.minSec; y <= this.maxSec; y++) {
            long node = iPortals$sectionId(x, y, z);
            int oldReferences = this.iPortals$nodeReferences.addTo(node, delta);
            int newReferences = oldReferences + delta;
            
            if (newReferences <= 0) {
                if (newReferences < 0) {
                    // This should be unreachable,
                    // since each view nets at most one change per node per apply.
                    // Clamping keeps a bug here from crashing the render thread.
                    Helper.LOGGER.warn(
                        "iPortals$ Voxy LoD node reference count went negative (was {}, delta {}); clamping",
                        oldReferences, delta
                    );
                }
                this.iPortals$nodeReferences.remove(node);
                if (oldReferences > 0) {
                    this.removeTopLevelNode.accept(node);
                }
            }
            else if (oldReferences <= 0) {
                this.addTopLevelNode.accept(node);
            }
        }
    }
    
    @Unique
    private static long iPortals$sectionId(int x, int y, int z) {
        // This duplicates WorldEngine.getWorldSectionId(4, x, y, z) to avoid linking against Voxy.
        return (4L << 60) | ((long) (y & 0xFF) << 52) |
            ((long) (z & 0xFFFFFF) << 28) | ((long) (x & 0xFFFFFF) << 4);
    }
    
    @Unique
    private static long iPortals$cellId(int x, int z) {
        return Integer.toUnsignedLong(x) | (Integer.toUnsignedLong(z) << 32);
    }
    
    @FunctionalInterface
    @Unique
    private interface iPortals$Applier {
        void apply(int x, int z, int delta);
    }
    
    // This class is static so that views do not hold a hidden reference to the outer mixin instance.
    @Unique
    private static final class iPortals$View {
        // Holds the cells this view wants loaded, including ones whose add is not drained yet.
        private final LongOpenHashSet desired = new LongOpenHashSet();
        // Holds the net change per cell that has not been applied yet.
        // A cell that is added and then removed before draining nets to 0 and never reaches Voxy.
        // Values are always -1, 0 or 1, because markAdd and markRemove are guarded by desired.
        private final Long2IntOpenHashMap pending = new Long2IntOpenHashMap();
        private int centerX = Integer.MIN_VALUE;
        private int centerZ = Integer.MIN_VALUE;
        private int builtRadius = -1;
        private int[] boundDist;
        private long lastSeenNanos;
        
        private void updateCenter(double x, double z, int radius) {
            int newCenterX = ((int) x) >> 9;
            int newCenterZ = ((int) z) >> 9;
            if (this.centerX == Integer.MIN_VALUE ||
                128 * 128 < (x - this.centerX * 512.0) * (x - this.centerX * 512.0) +
                    (z - this.centerZ * 512.0) * (z - this.centerZ * 512.0)) {
                
                int dx = newCenterX - this.centerX;
                int dz = newCenterZ - this.centerZ;
                
                if (this.centerX != Integer.MIN_VALUE && this.builtRadius == radius
                    && Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    // The camera moved by at most one grid cell, so only the boundary is updated.
                    this.ensureBoundDist(radius);
                    this.moveX(dx);
                    this.moveZ(dz);
                } else {
                    // A large camera jump such as a teleport needs a full rebuild.
                    // The first build and a radius change need one as well.
                    this.centerX = newCenterX;
                    this.centerZ = newCenterZ;
                    this.rebuild(radius);
                }
            }
        }
        
        private void ensureBoundDist(int radius) {
            if (this.boundDist == null) {
                this.boundDist = new int[radius * 2 + 1];
                for (int i = -radius; i <= radius; i++) {
                    this.boundDist[i + radius] = (int) Math.sqrt((double) radius * radius - (double) i * i);
                }
            }
        }
        
        private void moveX(int delta) {
            if (delta == 0) return;
            int radius = this.builtRadius;
            for (int i = 0; i <= radius * 2; i++) {
                int z = this.centerZ + i - radius;
                int d = this.boundDist[i] * delta;
                this.markAdd(iPortals$cellId(this.centerX + d + delta, z));
                this.markRemove(iPortals$cellId(this.centerX - d, z));
            }
            this.centerX += delta;
        }
        
        private void moveZ(int delta) {
            if (delta == 0) return;
            int radius = this.builtRadius;
            for (int i = 0; i <= radius * 2; i++) {
                int xx = this.centerX + i - radius;
                int d = this.boundDist[i] * delta;
                this.markAdd(iPortals$cellId(xx, this.centerZ + d + delta));
                this.markRemove(iPortals$cellId(xx, this.centerZ - d));
            }
            this.centerZ += delta;
        }
        
        private void markAdd(long cell) {
            if (this.desired.add(cell)) {
                this.pending.addTo(cell, 1);
            }
        }
        
        private void markRemove(long cell) {
            if (this.desired.remove(cell)) {
                this.pending.addTo(cell, -1);
            }
        }
        
        private void rebuild(int radius) {
            LongOpenHashSet next = new LongOpenHashSet();
            for (int dx = -radius; dx <= radius; dx++) {
                int dzLimit = (int) Math.sqrt((double) radius * radius - (double) dx * dx);
                for (int dz = -dzLimit; dz <= dzLimit; dz++) {
                    next.add(iPortals$cellId(this.centerX + dx, this.centerZ + dz));
                }
            }
            
            // The cells are copied first, because markRemove mutates desired during iteration.
            for (long cell : this.desired.toLongArray()) {
                if (!next.contains(cell)) {
                    this.markRemove(cell);
                }
            }
            for (long cell : next) {
                this.markAdd(cell);
            }
            
            this.builtRadius = radius;
            
            // The cached bound distances are now stale.
            // ensureBoundDist rebuilds them on the next incremental move.
            this.boundDist = null;
        }
        
        private void clear() {
            for (long cell : this.desired.toLongArray()) {
                this.markRemove(cell);
            }
        }
        
        /** Drains up to {@code budget} uncancelled changes and returns whether any work was done. */
        private boolean drain(int budget, iPortals$Applier applier) {
            if (budget <= 0 || this.pending.isEmpty()) {
                return false;
            }
            boolean didWork = false;
            int spent = 0;
            var iterator = this.pending.long2IntEntrySet().fastIterator();
            while (iterator.hasNext() && spent < budget) {
                Long2IntMap.Entry entry = iterator.next();
                int delta = entry.getIntValue();
                if (delta == 0) {
                    // The cell was added and then removed before draining,
                    // so it is dropped without costing budget or touching Voxy.
                    iterator.remove();
                    continue;
                }
                long cell = entry.getLongKey();
                iterator.remove();
                applier.apply((int) cell, (int) (cell >>> 32), delta);
                spent++;
                didWork = true;
            }
            return didWork;
        }
        
        /** Drains everything regardless of budget, and is only used when tearing a view down. */
        private void drainAll(iPortals$Applier applier) {
            var iterator = this.pending.long2IntEntrySet().fastIterator();
            while (iterator.hasNext()) {
                Long2IntMap.Entry entry = iterator.next();
                int delta = entry.getIntValue();
                long cell = entry.getLongKey();
                iterator.remove();
                if (delta != 0) {
                    applier.apply((int) cell, (int) (cell >>> 32), delta);
                }
            }
        }
    }
}
