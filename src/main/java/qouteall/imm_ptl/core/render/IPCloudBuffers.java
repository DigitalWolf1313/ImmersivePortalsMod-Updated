package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives each portal view its own cloud uniform buffer.
 * Vanilla writes the cloud position of every world render into one persistently mapped buffer
 * and only rotates it at the end of the frame.
 * The write is not ordered with the draws that are already submitted,
 * so a portal view would otherwise change the cloud position of the draws of the other views.
 * The buffers are used for three frames in turn, like vanilla's ring buffer.
 */
public class IPCloudBuffers {
    private static final int FRAME_SLOTS = 3;
    
    private static final int UBO_SIZE = new Std140SizeCalculator().putVec4().putVec3().putVec3().get();
    
    @SuppressWarnings("unchecked")
    private static final List<GpuBuffer>[] pools = new List[]{
        new ArrayList<GpuBuffer>(), new ArrayList<GpuBuffer>(), new ArrayList<GpuBuffer>()
    };
    
    private static int slot = 0;
    private static int used = 0;
    
    private static GpuBuffer current = null;
    
    /**
     * Returns a buffer that no other view uses in this frame, and makes it the current one.
     */
    public static GpuBuffer acquire() {
        List<GpuBuffer> pool = pools[slot];
        if (used >= pool.size()) {
            pool.add(RenderSystem.getDevice().createBuffer(
                () -> "Immersive Portals portal view clouds",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE,
                UBO_SIZE
            ));
        }
        current = pool.get(used++);
        return current;
    }
    
    public static void releaseCurrent() {
        current = null;
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
