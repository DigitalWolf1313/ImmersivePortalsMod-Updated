package qouteall.imm_ptl.core.compat;

import net.fabricmc.loader.api.FabricLoader;
import qouteall.imm_ptl.core.compat.sable_compatibility.IPSableIntegration;
import qouteall.q_misc_util.Helper;

public class IPSableCompat {

    public static boolean isSablePresent = false;
    public static boolean isIPSablePresent = false;

    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("sable")) {
            Helper.LOGGER.info("sable is present");
            isSablePresent = true;
        }

        if (IPSableIntegration.isIPSablePresent()) {
            Helper.LOGGER.info("IPSable is present");
            isIPSablePresent = true;
        }
    }
}