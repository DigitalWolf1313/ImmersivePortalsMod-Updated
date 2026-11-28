package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.fog.FogRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives each portal view its own fog uniform buffer.
 * Vanilla writes the fog of every world render into one persistently mapped buffer.
 * The write is not ordered with the draws that are already submitted,
 * so a portal view would otherwise change the fog of the draws of the view that contains it.
 * The buffers are used for three frames in turn, like vanilla's ring buffer.
 */
public class IPFogBuffers {
    private static final int FRAME_SLOTS = 3;
    
    @SuppressWarnings("unchecked")
    private static final List<GpuBuffer>[] pools = new List[]{
        new ArrayList<GpuBuffer>(), new ArrayList<GpuBuffer>(), new ArrayList<GpuBuffer>()
    };
    
    private static int slot = 0;
    private static int used = 0;
    
    // The buffer that the latest portal view fog setup wrote into.
    private static GpuBuffer current = null;
    
    /**
     * Returns a buffer that no other view uses in this frame, and makes it the current one.
     */
    public static GpuBuffer acquireForSetup() {
        List<GpuBuffer> pool = pools[slot];
        if (used >= pool.size()) {
            pool.add(RenderSystem.getDevice().createBuffer(
                () -> "Immersive Portals portal view fog",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE,
                FogRenderer.FOG_UBO_SIZE
            ));
        }
        current = pool.get(used++);
        return current;
    }
    
    public static GpuBuffer getCurrent() {
        return current;
    }
    
    public static void onEndFrame() {
        slot = (slot + 1) % FRAME_SLOTS;
        used = 0;
        current = null;
    }
}
