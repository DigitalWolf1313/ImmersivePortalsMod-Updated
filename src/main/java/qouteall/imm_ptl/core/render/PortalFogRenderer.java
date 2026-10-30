package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

/** Adds the current world's fog between the camera and a portal's surface. */
public class PortalFogRenderer {
    public static ShaderInstance shader;
    
    public record FogState(
        boolean enabled, float start, float end, int shape,
        float red, float green, float blue, float alpha
    ) {}
    
    public static FogState capture() {
        float[] color = RenderSystem.getShaderFogColor();
        return new FogState(
            WorldRenderInfo.isFogEnabled(),
            RenderSystem.getShaderFogStart(), RenderSystem.getShaderFogEnd(),
            RenderSystem.getShaderFogShape().getIndex(),
            color[0], color[1], color[2], color[3]
        );
    }
    
    /**
     * Call after restoring the portal surface's depth and before discarding its stencil mask.
     * The framebuffer renderer instead uses the depth written by its portal texture draw.
     */
    public static void render(Portal portal, Matrix4f modelView, FogState fog) {
        // Fuse-view portals have no surface and disable destination fog.
        if (!fog.enabled() || fog.alpha() <= 0 || portal.isFuseView()) {
            return;
        }
        
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        ShaderInstance previousShader = RenderSystem.getShader();
        
        try {
            if (PortalRendering.isRendering()) {
                FrontClipping.setupInnerClipping(
                    PortalRendering.getActiveClippingPlane(), modelView, 0
                );
            }
            else {
                FrontClipping.disableClipping();
            }
            
            RenderSystem.setShader(() -> shader);
            shader.MODEL_VIEW_MATRIX.set(modelView);
            shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
            shader.FOG_START.set(fog.start());
            shader.FOG_END.set(fog.end());
            shader.FOG_SHAPE.set(fog.shape());
            shader.FOG_COLOR.set(fog.red(), fog.green(), fog.blue(), fog.alpha());
            FrontClipping.updateClippingEquationUniformForCurrentShader(false);
            shader.apply();
            
            RenderSystem.enableBlend();
            // Vanilla fog changes RGB and preserves framebuffer alpha.
            RenderSystem.blendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE
            );
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            CHelper.enableDepthClamp();
            
            ViewAreaRenderer.buildPortalViewAreaTrianglesBuffer(
                Vec3.ZERO, portal, CHelper.getCurrentCameraPos(), RenderStates.getPartialTick()
            );
        }
        finally {
            CHelper.disableDepthClamp();
            shader.clear();
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.depthFunc(depthFunc);
            RenderSystem.depthMask(depthMask);
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull();
            else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
            FrontClipping.disableClipping();
        }
    }
}
