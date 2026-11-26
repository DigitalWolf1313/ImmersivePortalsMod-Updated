package qouteall.imm_ptl.core.compat.voxy_compatibility;

import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.texture.TextureType;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;

/**
 * Registers Voxy's depth samplers with Iris.
 * The textures are looked up from the level renderer that is rendering when the sampler is read.
 * This keeps the samplers correct when Immersive Portals switches between dimensions.
 */
public final class IPVoxyIrisSamplers {
    private IPVoxyIrisSamplers() {
    }
    
    public static void addSamplers(IrisRenderingPipeline pipeline, SamplerHolder samplers) {
        if (!IPVoxyPipelineBridge.hasPatchData(pipeline)) {
            return;
        }
        
        samplers.addDynamicSampler(
            TextureType.TEXTURE_2D,
            () -> IPVoxyPipelineBridge.getVoxyDepthTexture(false),
            new GlSampler(false, true, false, false),
            "vxDepthTexOpaque"
        );
        samplers.addDynamicSampler(
            TextureType.TEXTURE_2D,
            () -> IPVoxyPipelineBridge.getVoxyDepthTexture(true),
            new GlSampler(false, true, false, false),
            "vxDepthTexTrans"
        );
    }
}
