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
 * Keeps Voxy's requested LoD nodes alive for every view (outer camera + each portal)
 * rendered in a frame. Each view (keyed by its full portal path, mirroring
 * {@link MixinVoxyViewportSelector}) gets its own ring of desired nodes, and nodes shared
 * between overlapping views are reference-counted so they're only unloaded once every view
 * has released them.
 *
 * Voxy normally tracks a single camera, and portal rendering temporarily moves that camera;
 * without this mixin the outer and portal views would fight over Voxy's one ring of desired
 * nodes and constantly evict each other.
 */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.rendering.RenderDistanceTracker", remap = false)
public abstract class MixinVoxyRenderDistanceTracker {
    @Shadow private LongConsumer addTopLevelNode;
    @Shadow private LongConsumer removeTopLevelNode;
    @Shadow private int processRate;
    @Shadow private int minSec;
    @Shadow private int maxSec;

    // HashMap keyed by full portal path, mirroring MixinVoxyViewportSelector. The outer
    // camera is keyed by the empty path (what getPortalPath() returns when not rendering).
    //
    // Deliberately NOT initialized inline: Voxy's constructor calls setRenderDistance() on
    // the freshly-built object as part of its own <init>, and relying on Mixin's automatic
    // field-initializer merging for that ordering has proven unreliable for @Pseudo mixins.
    // iPortals$onConstructed initializes it instead.
    @Unique
    private Map<List<Portal>, iPortals$View> iPortals$views;

    // Reference counts so a node desired by more than one view is only actually unloaded
    // once every view referencing it has released it. Initialized in iPortals$onConstructed
    // for the same reason as iPortals$views.
    @Unique
    private Long2IntOpenHashMap iPortals$nodeReferences;

    @Unique
    private int iPortals$renderDistance;

    @Unique
    private static final long iPortals$DISCARD_GRACE_NANOS = 30_000_000_000L; // 30s

    // Mirrors the real constructor, which initializes renderDistance to 2 before any
    // setRenderDistance() call. Also sets up iPortals$views/iPortals$nodeReferences.
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
        // Empty list when rendering the outer world.
        List<Portal> path = PortalRendering.getPortalPath();
        long now = System.nanoTime();

        if (path.isEmpty()) {
            // Must run before discardInactiveViews: it may hand a view off to a new key
            // (refreshing its lastSeenNanos), and discard should see that fresh timestamp.
            this.iPortals$applyPendingRenderDistanceSwap(now);
            // Anchored on the outer camera's call since that's guaranteed every frame.
            this.iPortals$discardInactiveViews(now);
        }

        iPortals$View view = this.iPortals$views.computeIfAbsent(path, ignored -> new iPortals$View());
        view.lastSeenNanos = now;
        view.updateCenter(x, z, this.iPortals$renderDistance);

        // Only this view's own backlog is drained here; Voxy calls this once per view per
        // frame, so each view still gets its full intended per-frame budget.
        cir.setReturnValue(view.drain(this.processRate, this::iPortals$apply));
    }

    @Unique
    private static final List<Portal> iPortals$OUTER_PATH = List.of();

    /**
     * Consumes any pending same-dimension teleport notification and hands the outer and
     * portal-destination views off by identity, so neither has to rebuild its nodes from
     * scratch after teleporting.
     */
    @Unique
    private void iPortals$applyPendingRenderDistanceSwap(long now) {
        Portal swapPortal = IPVoxyCompat.consumeVoxyRenderDistanceSwap();
        if (swapPortal == null) {
            return;
        }

        // The view tracking the portal's destination (roughly where the player now stands);
        // null if the destination was never rendered as its own view.
        iPortals$View promoted = this.iPortals$views.remove(List.of(swapPortal));

        // The view tracking the area the player just left, still keyed under the outer path.
        iPortals$View previousOuter = this.iPortals$views.get(iPortals$OUTER_PATH);

        if (promoted != null) {
            this.iPortals$views.put(iPortals$OUTER_PATH, promoted);
            promoted.lastSeenNanos = now;
        }
        // else: no pre-built destination view to promote - the outer view keeps its current
        // object and does a normal rebuild in updateCenter below.

        if (previousOuter != null) {
            Portal reversePortal = PortalManipulation.findReversePortal(swapPortal);
            if (reversePortal != null) {
                // Hand the vacated outer view to the reverse portal, so looking back right
                // after teleporting reuses its already-resident nodes.
                List<Portal> reversePath = List.of(reversePortal);
                iPortals$View displaced = this.iPortals$views.put(reversePath, previousOuter);
                previousOuter.lastSeenNanos = now;
                if (displaced != null && displaced != previousOuter) {
                    // Stale view from an earlier look-back through the same reverse portal.
                    displaced.clear();
                    displaced.drainAll(this::iPortals$apply);
                }
            }
            else if (promoted != null) {
                // One-way portal (no reverse to hand this off to) and we just overwrote the
                // outer path's entry with `promoted` - release the orphaned view.
                previousOuter.clear();
                previousOuter.drainAll(this::iPortals$apply);
            }
            // else: one-way portal and no promoted view - previousOuter is still the live
            // outer view (nothing was put over it), so leave it alone.
        }
    }

    @Unique
    private void iPortals$discardInactiveViews(long now) {
        var iterator = this.iPortals$views.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().isEmpty()) {
                continue; // never discard the outer view
            }
            iPortals$View view = entry.getValue();
            if (now - view.lastSeenNanos <= iPortals$DISCARD_GRACE_NANOS) {
                continue;
            }
            // The portal hasn't been rendered for the grace window - release everything it
            // was holding alive. Unbudgeted: the view is about to be dropped, so anything
            // left pending would otherwise leak its retained nodes forever.
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
                    // Should be unreachable (each view only ever nets +-1 per node per
                    // apply), but clamp instead of crashing the render thread if it ever
                    // goes negative.
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
        // WorldEngine.getWorldSectionId(4, x, y, z), duplicated to avoid a Voxy linkage.
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

    // Static: doesn't touch the outer mixin instance, so no hidden this$0 per view.
    @Unique
    private static final class iPortals$View {
        // Cells this view currently wants loaded, whether or not the add has been drained yet.
        private final LongOpenHashSet desired = new LongOpenHashSet();
        // Net not-yet-applied delta per cell. A cell added then removed (or vice versa)
        // before being drained nets to 0 and never touches Voxy. Values are always in
        // {-1, 0, 1}: markAdd/markRemove are guarded by `desired` membership.
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
                    // Camera drifted by one grid cell; update only the boundary.
                    this.ensureBoundDist(radius);
                    this.moveX(dx);
                    this.moveZ(dz);
                } else {
                    // Large jump (teleport, first build, or radius changed): full rebuild.
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

            // Snapshot first: markRemove mutates `desired`, so we can't iterate it live.
            for (long cell : this.desired.toLongArray()) {
                if (!next.contains(cell)) {
                    this.markRemove(cell);
                }
            }
            for (long cell : next) {
                this.markAdd(cell);
            }

            this.builtRadius = radius;
            this.boundDist = null; // stale - lazily rebuilt by ensureBoundDist on the next incremental move
        }

        private void clear() {
            for (long cell : this.desired.toLongArray()) {
                this.markRemove(cell);
            }
        }

        /** Drains up to {@code budget} real (non-cancelled) changes. Returns whether any work was done. */
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
                    // Fully cancelled out (added then removed) before we got to it - drop
                    // silently, doesn't cost budget or touch Voxy.
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

        /** Drains everything regardless of budget - only used when tearing a view down entirely. */
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
