package qouteall.imm_ptl.core.compat.mixin.voxy;

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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Gives each portal its own persistent Voxy viewport, keyed by the full portal path.
 *
 * Voxy's viewport state is temporal, so reusing one viewport across cameras causes stale
 * draw calls/HiZ data when switching views. Portal-specific viewports keep them independent,
 * with a capped cache to bound GPU memory. Vivecraft per-eye and Iris shadow-pass viewports
 * are left untouched. When the player walks through a portal, the outer camera takes over
 * the viewport that was rendering the destination world.
 */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.rendering.ViewportSelector", remap = false)
public class MixinVoxyViewportSelector {

    @Shadow
    private Supplier creator;

    // The field's erased type is Viewport (not Object), so @Shadow fails Mixin's exact
    // descriptor match; read it via reflection instead.
    @Unique
    private static final java.lang.reflect.Field iPortals$defaultViewportField =
        Helper.noError(() -> {
            Class<?> clazz = Class.forName("me.cortex.voxy.client.core.rendering.ViewportSelector");
            java.lang.reflect.Field field = clazz.getDeclaredField("defaultViewport");
            field.setAccessible(true);
            return field;
        });

    // The field is final and set in the constructor, so it's safe to cache here.
    @Unique
    private Object iPortals$cachedDefaultViewport;

    @Unique
    private Object iPortals$getDefaultViewport() {
        return Helper.noError(() -> iPortals$defaultViewportField.get(this));
    }

    @Unique
    private static final int iPortals$MAX_PORTAL_VIEWPORTS = 10;

    // Access-ordered so the eldest entry is LRU-evictable. Deliberately not Voxy's own
    // extraViewports map, so portal entries don't collide with VR/Iris-shadow entries.
    @Unique
    private LinkedHashMap<List<Portal>, Object> iPortals$portalViewports;

    // The viewport backing the outer camera once a swap has happened, captured from Voxy's
    // own return value rather than shadowed.
    @Unique
    private Object iPortals$outerViewport;

    @Unique
    private boolean iPortals$haveSwappedOuter;

    // Viewports we created via creator.get(); never contains a Voxy-owned viewport, so
    // nothing is ever freed twice.
    @Unique
    private Set<Object> iPortals$ownedViewports;

    // Field-initializer merging is unreliable for @Pseudo mixins, so init here instead.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void iPortals$onConstructed(Supplier viewportCreator, CallbackInfo ci) {
        this.iPortals$cachedDefaultViewport = iPortals$getDefaultViewport();
        this.iPortals$portalViewports = new LinkedHashMap<>(8, 0.75f, true);
        this.iPortals$ownedViewports = new HashSet<>();
    }

    @Unique
    private void iPortals$evictOverflow() {
        while (this.iPortals$portalViewports.size() > iPortals$MAX_PORTAL_VIEWPORTS) {
            Map.Entry<List<Portal>, Object> eldest =
                this.iPortals$portalViewports.entrySet().iterator().next();
            // Never evict the live outer viewport - it would otherwise become the LRU
            // victim while still bound/sampled every frame.
            if (eldest.getValue() == this.iPortals$outerViewport) {
                break;
            }
            this.iPortals$portalViewports.remove(eldest.getKey());
            // Only free viewports we created; Voxy frees its own.
            if (this.iPortals$ownedViewports.remove(eldest.getValue())) {
                iPortals$deleteViewport(eldest.getValue());
            }
        }
    }

    // Gated on the original result being Voxy's own defaultViewport, so Vivecraft per-eye
    // and Iris shadow-pass viewports are left alone.
    @Inject(method = "getViewport", at = @At("RETURN"), cancellable = true)
    private void iPortals$selectViewport(CallbackInfoReturnable<Object> cir) {
        Object defaultViewport = this.iPortals$cachedDefaultViewport;
        if (defaultViewport == null || cir.getReturnValue() != defaultViewport) {
            return;
        }

        if (PortalRendering.isRendering()) {
            List<Portal> path = PortalRendering.getPortalPath();
            // Voxy already manages its own viewport for cross-dimension portals.
            if (path.stream().anyMatch(portal -> !portal.level().dimension().equals(portal.getDestDim()))) {
                return;
            }
            Object viewport = this.iPortals$portalViewports.get(path);
            if (viewport == null) {
                viewport = this.creator.get();
                this.iPortals$ownedViewports.add(viewport);
                this.iPortals$portalViewports.put(path, viewport);
                this.iPortals$evictOverflow();
            }
            cir.setReturnValue(viewport);
            return;
        }

        // True outer camera - apply any pending teleport swap.
        Portal swapPortal = IPVoxyCompat.consumeVoxyViewportSwap();
        if (swapPortal != null) {
            // Remove (not peek): the viewport is promoted to the outer viewport, so it
            // must stop being reachable under its old portal-path key.
            Object portalViewport = this.iPortals$portalViewports.remove(List.of(swapPortal));
            if (portalViewport != null) {
                Object previousOuter =
                    this.iPortals$haveSwappedOuter ? this.iPortals$outerViewport : defaultViewport;

                this.iPortals$outerViewport = portalViewport;
                this.iPortals$haveSwappedOuter = true;

                // Hand the vacated outer viewport to the reverse portal, so looking back
                // right after teleporting doesn't start from a blank viewport.
                Portal reversePortal = PortalManipulation.findReversePortal(swapPortal);
                if (reversePortal != null) {
                    Object displaced =
                        this.iPortals$portalViewports.put(List.of(reversePortal), previousOuter);
                    this.iPortals$evictOverflow();
                    // displaced may be a stale viewport from an earlier look-back; free it.
                    if (displaced != null && displaced != previousOuter
                        && this.iPortals$ownedViewports.remove(displaced)) {
                        iPortals$deleteViewport(displaced);
                    }
                }
                else if (this.iPortals$ownedViewports.remove(previousOuter)) {
                    // One-way portal, nothing to hand this off to - free it.
                    iPortals$deleteViewport(previousOuter);
                }
            }
        }

        if (this.iPortals$haveSwappedOuter) {
            cir.setReturnValue(this.iPortals$outerViewport);
        }
    }

    @Inject(method = "free", at = @At("HEAD"))
    private void iPortals$onFree(CallbackInfo ci) {
        if (iPortals$ownedViewports.remove(this.iPortals$outerViewport)) {
            iPortals$deleteViewport(this.iPortals$outerViewport);
        }
        for (Object viewport : this.iPortals$portalViewports.values()) {
            if (iPortals$ownedViewports.remove(viewport)) {
                iPortals$deleteViewport(viewport);
            }
        }
        this.iPortals$portalViewports.clear();
        this.iPortals$outerViewport = null;
        this.iPortals$haveSwappedOuter = false;
        this.iPortals$ownedViewports.clear();
    }

    // Cached `delete` Method per concrete viewport class, so the reflection lookup happens
    // at most once per class.
    @Unique
    private static final Map<Class<?>, java.lang.reflect.Method> iPortals$deleteMethods =
        new ConcurrentHashMap<>();

    @Unique
    private static void iPortals$deleteViewport(Object viewport) {
        Class<?> clazz = viewport.getClass();
        java.lang.reflect.Method deleteMethod = iPortals$deleteMethods.computeIfAbsent(
            clazz,
            c -> Helper.noError(() -> c.getMethod("delete"))
        );
        Helper.noError(() -> {
            deleteMethod.invoke(viewport);
            return null;
        });
    }
}
