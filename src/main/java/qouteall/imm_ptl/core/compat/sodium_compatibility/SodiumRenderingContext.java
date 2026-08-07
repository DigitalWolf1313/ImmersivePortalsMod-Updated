package qouteall.imm_ptl.core.compat.sodium_compatibility;

import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public class SodiumRenderingContext {
    public SortedRenderLists renderLists;
    
    public int renderDistance;
    
    public Vector3d lastCameraPos;
    
    public Vector3dc cameraPosition;
    
    public SodiumRenderingContext(int renderDistance, Vector3d initialCameraPos) {
        this.renderDistance = renderDistance;
        this.renderLists = SortedRenderLists.empty();
        this.lastCameraPos = initialCameraPos;
        this.cameraPosition = initialCameraPos;
    }
}
