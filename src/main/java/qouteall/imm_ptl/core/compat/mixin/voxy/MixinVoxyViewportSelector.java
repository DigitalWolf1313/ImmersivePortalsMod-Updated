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
 * Voxy's viewport state is temporal, so sharing one viewport between cameras leaves
 * stale draw calls and HiZ data behind.
 * The cache is capped to bound GPU memory.
 * Vivecraft per-eye and Iris shadow-pass viewports are left untouched.
 * After the player walks through a portal, the outer camera takes over the viewport
 * that was rendering the destination world.
 */
@Pseudo
@Mixin(targets = "me.cortex.voxy.client.core.rendering.ViewportSelector", remap = false)
public class MixinVoxyViewportSelector {
    
    @Shadow
    private Supplier creator;
    
    // The field's erased type is Viewport rather than Object,
    // so @Shadow fails Mixin's exact descriptor match and reflection reads it instead.
    @Unique
    private static final java.lang.reflect.Field iPortals$defaultViewportField =
        Helper.noError(() -> {
            Class<?> clazz = Class.forName("me.cortex.voxy.client.core.rendering.ViewportSelector");
            java.lang.reflect.Field field = clazz.getDeclaredField("defaultViewport");
            field.setAccessible(true);
            return field;
        });
    
    // The field is final and assigned in the constructor, so caching it is safe.
    @Unique
    private Object iPortals$cachedDefaultViewport;
    
    @Unique
    private Object iPortals$getDefaultViewport() {
        return Helper.noError(() -> iPortals$defaultViewportField.get(this));
    }
    
    @Unique
    private static final int iPortals$MAX_PORTAL_VIEWPORTS = 30;
    
    // The map is access ordered, so the eldest entry is the least recently used one.
    // It is separate from Voxy's extraViewports map,
    // so portal entries never collide with VR or Iris shadow entries.
    @Unique
    private LinkedHashMap<List<Portal>, Object> iPortals$portalViewports;
    
    // The viewport backing the outer camera once a swap has happened.
    // It is captured from Voxy's own return value.
    @Unique
    private Object iPortals$outerViewport;
    
    @Unique
    private boolean iPortals$haveSwappedOuter;
    
    // Holds the viewports created through creator.get().
    // It never contains a Voxy-owned viewport, so nothing is freed twice.
    @Unique
    private Set<Object> iPortals$ownedViewports;
    
    // Field initializer merging is unreliable for @Pseudo mixins, so the fields are set here.
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
            // The live outer viewport is never evicted,
            // since it is still bound and sampled every frame.
            if (eldest.getValue() == this.iPortals$outerViewport) {
                break;
            }
            this.iPortals$portalViewports.remove(eldest.getKey());
            // Only viewports created here are freed, because Voxy frees its own.
            if (this.iPortals$ownedViewports.remove(eldest.getValue())) {
                iPortals$deleteViewport(eldest.getValue());
            }
        }
    }
    
    // This only acts when the original result is Voxy's own defaultViewport,
    // so Vivecraft per-eye and Iris shadow-pass viewports are left alone.
    @Inject(method = "getViewport", at = @At("RETURN"), cancellable = true)
    private void iPortals$selectViewport(CallbackInfoReturnable<Object> cir) {
        Object defaultViewport = this.iPortals$cachedDefaultViewport;
        if (defaultViewport == null || cir.getReturnValue() != defaultViewport) {
            return;
        }
        
        if (PortalRendering.isRendering()) {
            List<Portal> path = PortalRendering.getPortalPath();
            // A cross-dimension portal keeps Voxy's own viewport.
            // A same-dimension portal nested inside it still needs a path-specific viewport.
            if (!Portal.isSameDimensionPortal(PortalRendering.getRenderingPortal())) {
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
        
        // This is the true outer camera, so any pending teleport swap is applied.
        Portal swapPortal = IPVoxyCompat.consumeVoxyViewportSwap();
        if (swapPortal != null) {
            // The viewport is removed rather than peeked, since it becomes the outer viewport
            // and must no longer be reachable under its old portal-path key.
            Object portalViewport = this.iPortals$portalViewports.remove(List.of(swapPortal));
            if (portalViewport != null) {
                Object previousOuter =
                    this.iPortals$haveSwappedOuter ? this.iPortals$outerViewport : defaultViewport;
                
                this.iPortals$outerViewport = portalViewport;
                this.iPortals$haveSwappedOuter = true;
                
                // The vacated outer viewport goes to the reverse portal,
                // so looking back right after teleporting doesn't start from a blank viewport.
                Portal reversePortal = PortalManipulation.findReversePortal(swapPortal);
                if (reversePortal != null) {
                    Object displaced =
                        this.iPortals$portalViewports.put(List.of(reversePortal), previousOuter);
                    this.iPortals$evictOverflow();
                    // The displaced viewport may be stale from an earlier look-back, so it is freed.
                    if (displaced != null && displaced != previousOuter
                        && this.iPortals$ownedViewports.remove(displaced)) {
                        iPortals$deleteViewport(displaced);
                    }
                }
                else if (this.iPortals$ownedViewports.remove(previousOuter)) {
                    // A one-way portal has no reverse portal to receive it, so it is freed.
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
    
    // Caches the delete method per concrete viewport class,
    // so the reflection lookup happens at most once per class.
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
