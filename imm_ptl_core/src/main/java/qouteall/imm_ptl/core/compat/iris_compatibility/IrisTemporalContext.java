package qouteall.imm_ptl.core.compat.iris_compatibility;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

public final class IrisTemporalContext {
    private static IrisTemporalViewState current;
    private static long uniformEpoch;
    
    public static boolean isEnabled() {
        return IPCGlobal.irisTemporalHistory && (
            IPCGlobal.renderer instanceof IrisPortalRenderer
                || IPCGlobal.renderer instanceof IrisCompatibilityPortalRenderer
        );
    }
    
    public static IrisTemporalViewState current() {
        return isEnabled() ? current : null;
    }
    
    public static long uniformEpoch() {
        return uniformEpoch;
    }
    
    public static void activate(IrisTemporalViewState view) {
        current = view;
        uniformEpoch++;
    }
    
    /** These renderers finish the parent composite before recursively rendering children. */
    public static void renderWorld(WorldRenderInfo info) {
        if (!isEnabled()) {
            IrisInterface.invoker.updatePerFrameUniforms();
            MyGameRenderer.renderWorldNew(info, Runnable::run);
            return;
        }
        
        IrisTemporalViewState parent = current;
        CapturedRenderingState captured = CapturedRenderingState.INSTANCE;
        Matrix4f modelView = new Matrix4f(captured.getGbufferModelView());
        Matrix4f projection = new Matrix4f(captured.getGbufferProjection());
        Vector3d fog = new Vector3d(captured.getFogColor());
        float fogDensity = captured.getFogDensity();
        try {
            MyGameRenderer.renderWorldNew(info, Runnable::run);
        }
        finally {
            activate(parent);
            captured.setGbufferModelView(modelView);
            captured.setGbufferProjection(projection);
            captured.setFogColor((float) fog.x, (float) fog.y, (float) fog.z);
            captured.setFogDensity(fogDensity);
            if (Iris.getPipelineManager().getPipelineNullable() instanceof IEIrisNewWorldRenderingPipeline pipeline) {
                pipeline.ip_refreshTemporalUniforms();
            }
        }
    }
}
