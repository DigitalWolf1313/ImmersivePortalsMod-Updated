package qouteall.imm_ptl.core.compat.iris_compatibility;

import net.irisshaders.iris.targets.RenderTargets;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPCGlobal;

// Iris makes one pipeline (with its own RenderTargets) per dimension, so a cross-dimension portal
// would render into G-buffers that the outer composite never reads. Share one RenderTargets instead.
public class IrisRenderTargetSharing {
    @Nullable
    private static RenderTargets sharedRenderTargets;
    private static boolean sharedRenderTargetsDestroyed;
    private static int resizeGeneration;
    
    public static boolean shouldShare() {
        return IPCGlobal.renderer instanceof ExperimentalIrisPortalRenderer;
    }
    
    @Nullable
    public static RenderTargets getReusableRenderTargets() {
        if (sharedRenderTargets != null && !sharedRenderTargetsDestroyed) {
            return sharedRenderTargets;
        }
        return null;
    }
    
    public static void registerRenderTargets(RenderTargets renderTargets) {
        sharedRenderTargets = renderTargets;
        sharedRenderTargetsDestroyed = false;
        resizeGeneration = 0;
    }
    
    public static boolean isShared(RenderTargets renderTargets) {
        return renderTargets == sharedRenderTargets;
    }
    
    // all sharing pipelines hold the same instance, so only destroy it once
    public static boolean shouldDestroy(RenderTargets renderTargets) {
        if (renderTargets != sharedRenderTargets) {
            return true;
        }
        if (sharedRenderTargetsDestroyed) {
            return false;
        }
        sharedRenderTargetsDestroyed = true;
        return true;
    }
    
    // Iris only reports a resize to the first pipeline that checks it, so each pipeline tracks the generation
    public static void onResized() {
        resizeGeneration++;
    }
    
    public static int getResizeGeneration() {
        return resizeGeneration;
    }
}
