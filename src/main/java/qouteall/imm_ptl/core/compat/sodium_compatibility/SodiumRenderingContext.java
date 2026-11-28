package qouteall.imm_ptl.core.compat.sodium_compatibility;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

public class SodiumRenderingContext {
    public SortedRenderLists renderLists;
    
    public int renderDistance;
    
    // Null until the first terrain update of this context.
    public @Nullable Vector3d lastCameraPos = null;
    
    public SodiumRenderingContext(int renderDistance) {
        this.renderDistance = renderDistance;
        this.renderLists = SortedRenderLists.empty();
    }
}
