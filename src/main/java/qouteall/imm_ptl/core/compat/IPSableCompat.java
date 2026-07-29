package qouteall.imm_ptl.core.compat;

import net.fabricmc.loader.api.FabricLoader;
import qouteall.q_misc_util.Helper;


public class IPSableCompat {

    public static boolean isSablePresent = false;
    public static boolean isIPSablePresent = false;

    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("sable")) {
            Helper.LOGGER.info("sable is present");
            isSablePresent = true;
            // Use reflection to check for IPSable... There is no other way to check.
            try {
                Class.forName("ipl.sable.SableBridge");
                isIPSablePresent = true;
                Helper.LOGGER.info("IPSable is present");
            }
            catch (ClassNotFoundException e) {
                // IPSable is not present, which is fine
            }
        }
    }
}
