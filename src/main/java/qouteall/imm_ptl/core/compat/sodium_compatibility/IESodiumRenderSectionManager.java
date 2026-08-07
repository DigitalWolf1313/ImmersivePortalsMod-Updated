package qouteall.imm_ptl.core.compat.sodium_compatibility;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import org.jetbrains.annotations.Nullable;

public interface IESodiumRenderSectionManager {
    void ip_swapContext(SodiumRenderingContext context);
    
    /**
     * @param otherViewRenderLists If non-null, sections are only force-sorted if they
     *                             are also present (with geometry) in this render list,
     *                             i.e. also visible from another view/camera.
     */
    void ip_forceBlockingSortCatchUp(@Nullable SortedRenderLists otherViewRenderLists);
}
