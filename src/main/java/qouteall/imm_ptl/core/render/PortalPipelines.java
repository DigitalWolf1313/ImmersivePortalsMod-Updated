package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * The render pipelines used for drawing portal view areas, portal fog and framebuffer blits.
 * A pipeline fixes the depth, cull, blend and write-mask state, so each state combination is its own pipeline.
 * The variants are created on first use and kept for the rest of the session.
 */
public class PortalPipelines {
    private static final String NAMESPACE = "immersive_portals";
    
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }
    
    private static RenderPipeline.Builder withMatrices(RenderPipeline.Builder builder) {
        return builder
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER);
    }
    
    private static final Map<Integer, RenderPipeline> portalAreaPipelines = new HashMap<>();
    
    /**
     * Draws the portal view area as colored triangles.
     * The fragment color is the fog color when the area is used as a placeholder for a not-yet-rendered portal.
     */
    public static RenderPipeline portalArea(boolean cull, boolean writeColor, boolean writeDepth) {
        int key = (cull ? 1 : 0) | (writeColor ? 2 : 0) | (writeDepth ? 4 : 0);
        return portalAreaPipelines.computeIfAbsent(key, k -> withMatrices(RenderPipeline.builder())
            .withLocation(id("pipeline/portal_area_" + k))
            .withVertexShader(id("core/portal_area"))
            .withFragmentShader(id("core/portal_area"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(cull)
            .withColorWrite(writeColor, writeColor)
            .withDepthWrite(writeDepth)
            .withoutBlend()
            .build()
        );
    }
    
    private static final Map<Integer, RenderPipeline> drawFbInAreaPipelines = new HashMap<>();
    
    /**
     * Draws a framebuffer's color into the screen-space region covered by the portal view area.
     * It samples with the fragment position, so the sampled framebuffer must have the same size as the target.
     */
    public static RenderPipeline drawFbInArea(boolean cull) {
        int key = cull ? 1 : 0;
        return drawFbInAreaPipelines.computeIfAbsent(key, k -> withMatrices(RenderPipeline.builder())
            .withLocation(id("pipeline/portal_draw_fb_in_area_" + k))
            .withVertexShader(id("core/portal_draw_fb_in_area"))
            .withFragmentShader(id("core/portal_draw_fb_in_area"))
            .withSampler("DiffuseSampler")
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withCull(cull)
            .withColorWrite(true, true)
            .withDepthWrite(true)
            .withoutBlend()
            .build()
        );
    }
    
    private static RenderPipeline portalFogPipeline;
    
    /**
     * Blends the world's fog color over the portal view area.
     * It reads the vanilla "Fog" uniform buffer.
     */
    public static RenderPipeline portalFog() {
        if (portalFogPipeline == null) {
            portalFogPipeline = withMatrices(RenderPipeline.builder())
                .withLocation(id("pipeline/portal_fog"))
                .withVertexShader(id("core/portal_fog"))
                .withFragmentShader(id("core/portal_fog"))
                .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withCull(false)
                // Vanilla fog changes RGB and preserves framebuffer alpha.
                .withBlend(new BlendFunction(
                    SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA,
                    SourceFactor.ZERO, DestFactor.ONE
                ))
                .withColorWrite(true, true)
                .withDepthWrite(false)
                .build();
        }
        return portalFogPipeline;
    }
    
    private static final Map<Integer, RenderPipeline> blitPipelines = new HashMap<>();
    
    /**
     * Draws a framebuffer onto the current target with a unit quad.
     * With alpha blending it uses vanilla's blit shader and treats the source as premultiplied.
     * Without it, the source is copied with the alpha forced to one.
     */
    public static RenderPipeline blit(boolean alphaBlend, boolean writeAlpha) {
        int key = (alphaBlend ? 1 : 0) | (writeAlpha ? 2 : 0);
        return blitPipelines.computeIfAbsent(key, k -> {
            RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(id("pipeline/blit_" + k))
                .withVertexShader(alphaBlend ? ResourceLocation.withDefaultNamespace("core/blit_screen") : id("core/blit_screen_noblend"))
                .withFragmentShader(alphaBlend ? ResourceLocation.withDefaultNamespace("core/blit_screen") : id("core/blit_screen_noblend"))
                .withSampler("InSampler")
                .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withCull(false)
                .withColorWrite(true, writeAlpha)
                .withDepthWrite(false);
            
            if (alphaBlend) {
                // The framebuffer holds premultiplied color when it contains translucent things.
                builder.withBlend(new BlendFunction(
                    SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA,
                    SourceFactor.ZERO, DestFactor.ONE
                ));
            }
            else {
                builder.withoutBlend();
            }
            return builder.build();
        });
    }
    
    private static RenderPipeline portalOverlayPipeline;
    
    /**
     * Draws the block overlay of a breakable portal.
     * It reuses the shader, samplers and uniforms of the translucent item entity pipeline,
     * but it does not write depth and it is biased towards the camera.
     */
    public static RenderPipeline portalOverlay() {
        if (portalOverlayPipeline == null) {
            RenderPipeline base = RenderPipelines.ITEM_ENTITY_TRANSLUCENT_CULL;
            RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(id("pipeline/portal_overlay"))
                .withVertexShader(base.getVertexShader())
                .withFragmentShader(base.getFragmentShader())
                .withVertexFormat(base.getVertexFormat(), base.getVertexFormatMode())
                .withBlend(BlendFunction.TRANSLUCENT)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .withDepthBias(-1.0f, -10.0f);
            for (String sampler : base.getSamplers()) {
                builder.withSampler(sampler);
            }
            for (RenderPipeline.UniformDescription uniform : base.getUniforms()) {
                if (uniform.textureFormat() == null) {
                    builder.withUniform(uniform.name(), uniform.type());
                }
                else {
                    builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
                }
            }
            portalOverlayPipeline = builder.build();
        }
        return portalOverlayPipeline;
    }
    
    private static RenderPipeline solidColorPipeline;
    
    /**
     * Draws solid-colored triangles in clip space.
     */
    public static RenderPipeline solidColor() {
        if (solidColorPipeline == null) {
            solidColorPipeline = withMatrices(RenderPipeline.builder())
                .withLocation(id("pipeline/solid_color"))
                .withVertexShader(ResourceLocation.withDefaultNamespace("core/position_color"))
                .withFragmentShader(ResourceLocation.withDefaultNamespace("core/position_color"))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withCull(false)
                .withColorWrite(true, true)
                .withDepthWrite(false)
                .withBlend(BlendFunction.TRANSLUCENT)
                .build();
        }
        return solidColorPipeline;
    }
    
    private static final Map<Integer, RenderPipeline> screenTrianglePipelines = new HashMap<>();
    
    /**
     * Draws solid-colored triangles given in clip space, without blending.
     * The depth test uses the less-or-equal function when enabled.
     * Depth only gets written when the depth test is enabled.
     */
    public static RenderPipeline screenTriangle(boolean writeColor, boolean writeDepth, boolean depthTest) {
        int key = (writeColor ? 1 : 0) | (writeDepth ? 2 : 0) | (depthTest ? 4 : 0);
        return screenTrianglePipelines.computeIfAbsent(key, k -> withMatrices(RenderPipeline.builder())
            .withLocation(id("pipeline/screen_triangle_" + k))
            .withVertexShader(ResourceLocation.withDefaultNamespace("core/position_color"))
            .withFragmentShader(ResourceLocation.withDefaultNamespace("core/position_color"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
            .withDepthTestFunction(depthTest ? DepthTestFunction.LEQUAL_DEPTH_TEST : DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .withColorWrite(writeColor, writeColor)
            .withDepthWrite(writeDepth)
            .withoutBlend()
            .build()
        );
    }
}
