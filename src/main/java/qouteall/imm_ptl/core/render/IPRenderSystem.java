package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla only keeps the projection matrix as a uniform buffer slice.
 * This remembers which matrix was written into each slice so that portal rendering can read it back.
 */
public class IPRenderSystem {
    private static final Map<GpuBufferSlice, Matrix4f> slicedMatrices = new HashMap<>();
    
    private static PerspectiveProjectionMatrixBuffer ownProjectionBuffer;
    private static PerspectiveProjectionMatrixBuffer scratchProjectionBuffer;
    
    /**
     * Called by the buffer mixin each time a matrix is written into a vanilla projection buffer.
     */
    public static void recordProjectionMatrix(GpuBufferSlice slice, Matrix4f matrix) {
        slicedMatrices.computeIfAbsent(slice, k -> new Matrix4f()).set(matrix);
    }
    
    /**
     * Returns a copy of the matrix in the currently bound projection buffer.
     * Returns identity when the buffer was not written through a known path.
     */
    public static Matrix4f getProjectionMatrix() {
        GpuBufferSlice slice = RenderSystem.getProjectionMatrixBuffer();
        Matrix4f matrix = slice == null ? null : slicedMatrices.get(slice);
        return matrix == null ? new Matrix4f() : new Matrix4f(matrix);
    }
    
    /**
     * Writes the matrix into a buffer owned by this class and binds it as the projection.
     */
    public static void setProjectionMatrix(Matrix4f matrix, ProjectionType type) {
        if (ownProjectionBuffer == null) {
            ownProjectionBuffer = new PerspectiveProjectionMatrixBuffer("immersive_portals");
        }
        RenderSystem.setProjectionMatrix(ownProjectionBuffer.getBuffer(matrix), type);
    }
    
    /**
     * Uploads the matrix to a scratch buffer without binding it as the projection.
     * The returned slice is overwritten by the next call.
     */
    public static GpuBufferSlice uploadProjectionMatrix(Matrix4f matrix) {
        if (scratchProjectionBuffer == null) {
            scratchProjectionBuffer = new PerspectiveProjectionMatrixBuffer("immersive_portals_scratch");
        }
        return scratchProjectionBuffer.getBuffer(matrix);
    }
}
