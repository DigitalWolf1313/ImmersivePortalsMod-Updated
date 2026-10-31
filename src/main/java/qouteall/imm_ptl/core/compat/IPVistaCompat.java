package qouteall.imm_ptl.core.compat;

import qouteall.q_misc_util.Helper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

// reason I Use this instead of a invoker/interface
// is just because there would be a warning during remapping
public class IPVistaCompat {
    
    private static final String VIEW_FINDER_CONTROLLER_CLASS =
        "net.mehvahdjukaar.vista.client.ViewFinderController";
    
    private static boolean lookedUp = false;
    
    // This is null if Vista is absent or its API is not as expected.
    private static MethodHandle isActiveHandle = null;
    
    /**
     * Returns whether Vista's view finder is active.
     * Returns false when Vista is not installed.
     */
    public static boolean isViewFinderActive() {
        if (!lookedUp) {
            lookedUp = true;
            isActiveHandle = lookupIsActive();
        }
        
        if (isActiveHandle == null) {
            return false;
        }
        
        try {
            return (boolean) isActiveHandle.invokeExact();
        }
        catch (Throwable e) {
            Helper.LOGGER.error("Failed to call Vista ViewFinderController.isActive", e);
            isActiveHandle = null;
            return false;
        }
    }
    
    private static MethodHandle lookupIsActive() {
        Class<?> clazz;
        try {
            clazz = Class.forName(VIEW_FINDER_CONTROLLER_CLASS);
        }
        catch (ClassNotFoundException e) {
            return null;
        }
        catch (Throwable e) {
            Helper.LOGGER.error("Failed to load Vista ViewFinderController", e);
            return null;
        }
        
        try {
            return MethodHandles.publicLookup().findStatic(
                clazz, "isActive", MethodType.methodType(boolean.class)
            );
        }
        catch (Throwable e) {
            Helper.LOGGER.error("Failed to find Vista ViewFinderController.isActive", e);
            return null;
        }
    }
}
