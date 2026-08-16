package qouteall.imm_ptl.core.compat;

import net.fabricmc.loader.api.FabricLoader;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;

public class IPVoxyCompat {
    
    public static boolean isVoxyPresent = false;
    
    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("voxy")) {
            Helper.LOGGER.info("Voxy is present");
            isVoxyPresent = true;
        }
    }

    private static Portal pendingVoxyViewportSwap;
    // Separate from pendingVoxyViewportSwap even though both are set together: the two
    // mixins consume independently in the same frame (MixinVoxyViewportSelector's
    // getViewport() runs before MixinVoxyRenderDistanceTracker's setCenterAndProcess()
    // for the outer camera's pass), so sharing one field would let the first consumer
    // clear it before the second ever sees it.
    private static Portal pendingVoxyRenderDistanceSwap;

    public static void requestVoxyViewportSwap(Portal portal) {
        if (!isVoxyPresent) {
            return;
        }
        if (portal == null || !portal.level().dimension().equals(portal.getDestDim())) {
            return;
        }
        pendingVoxyViewportSwap = portal;
        pendingVoxyRenderDistanceSwap = portal;
    }

    public static Portal consumeVoxyViewportSwap() {
        Portal portal = pendingVoxyViewportSwap;
        pendingVoxyViewportSwap = null;
        return portal;
    }

    public static Portal consumeVoxyRenderDistanceSwap() {
        Portal portal = pendingVoxyRenderDistanceSwap;
        pendingVoxyRenderDistanceSwap = null;
        return portal;
    }
}
