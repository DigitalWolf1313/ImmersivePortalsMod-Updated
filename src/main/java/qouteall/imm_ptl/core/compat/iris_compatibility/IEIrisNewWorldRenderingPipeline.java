package qouteall.imm_ptl.core.compat.iris_compatibility;

import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.shadows.ShadowRenderTargets;

public interface IEIrisNewWorldRenderingPipeline {
    void ip_setIsRenderingWorld(boolean cond);
    
    ShadowRenderTargets ip_getShadowRenderTargets();
    
    void ip_refreshFrameState();

    GlFramebuffer ip_getPortalFogFramebuffer();
}
