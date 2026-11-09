package qouteall.imm_ptl.core.compat.iris_compatibility;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

/**
 * Stores the camera history of one view.
 * It advances once per displayed frame and never when a uniform is read.
 */
public final class IrisTemporalViewState {
    public final Vector3d camera = new Vector3d();
    public final Vector3d previousCamera = new Vector3d();
    public final Vector3d unshiftedCamera = new Vector3d();
    public final Vector3d previousUnshiftedCamera = new Vector3d();
    public final Matrix4f modelView = new Matrix4f();
    public final Matrix4f projection = new Matrix4f();
    public final Matrix4f previousModelView = new Matrix4f();
    public final Matrix4f previousProjection = new Matrix4f();
    private final Vector3d shift = new Vector3d();
    private boolean initialized;
    private int frame;
    private int width;
    private int height;
    
    /** Returns whether the stored image history can still be used. */
    public boolean begin(int newFrame, Vector3d position, Matrix4fc view, Matrix4fc proj,
                         int newWidth, int newHeight, boolean cameraCut) {
        boolean continuous = initialized && (newFrame == frame || newFrame == frame + 1)
            && width == newWidth && height == newHeight && !cameraCut
            && position.distanceSquared(unshiftedCamera) < 256.0;
        if (initialized && newFrame != frame) {
            previousUnshiftedCamera.set(unshiftedCamera);
            previousModelView.set(modelView);
            previousProjection.set(projection);
        }
        unshiftedCamera.set(position);
        modelView.set(view);
        projection.set(proj);
        if (!continuous) {
            previousUnshiftedCamera.set(position);
            previousModelView.set(view);
            previousProjection.set(proj);
        }
        
        // Matches Iris's floating-origin convention and uses the same origin for both frames.
        double x = position.x + shift.x;
        double z = position.z + shift.z;
        if (Math.abs(x) > 30000) shift.x -= x - x % 30000;
        if (Math.abs(z) > 30000) shift.z -= z - z % 30000;
        camera.set(position).add(shift);
        previousCamera.set(previousUnshiftedCamera).add(shift);
        
        frame = newFrame;
        width = newWidth;
        height = newHeight;
        initialized = true;
        return continuous;
    }
}
