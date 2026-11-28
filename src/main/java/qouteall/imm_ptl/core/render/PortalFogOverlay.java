package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.system.MemoryStack;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.Helper;

import java.nio.ByteBuffer;

/** Adds the current world's fog between the camera and a portal's surface. */
public class PortalFogOverlay {
    public record FogState(
        boolean enabled, float red, float green, float blue, float alpha,
        float environmentalStart, float environmentalEnd,
        float renderDistanceStart, float renderDistanceEnd,
        float skyEnd, float cloudEnd
    ) {}
    
    private static final FogState NO_FOG = new FogState(
        false, 0, 0, 0, 0, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
        Float.MAX_VALUE, Float.MAX_VALUE
    );
    
    private static FogState lastWorldFog = NO_FOG;
    
    /**
     * Called by the fog renderer mixin with the values that vanilla writes into the fog uniform buffer.
     * The buffer is overwritten by the next world that gets rendered, so the values are kept here.
     */
    public static void recordWorldFog(
        boolean vanillaFogEnabled, Vector4f color,
        float environmentalStart, float environmentalEnd,
        float renderDistanceStart, float renderDistanceEnd,
        float skyEnd, float cloudEnd
    ) {
        lastWorldFog = new FogState(
            vanillaFogEnabled, color.x(), color.y(), color.z(), color.w(),
            environmentalStart, environmentalEnd, renderDistanceStart, renderDistanceEnd,
            skyEnd, cloudEnd
        );
    }
    
    public static FogState capture() {
        FogState fog = lastWorldFog;
        return new FogState(
            fog.enabled() && WorldRenderInfo.isFogEnabled(),
            fog.red(), fog.green(), fog.blue(), fog.alpha(),
            fog.environmentalStart(), fog.environmentalEnd(),
            fog.renderDistanceStart(), fog.renderDistanceEnd(),
            fog.skyEnd(), fog.cloudEnd()
        );
    }
    
    /**
     * Returns the values that the latest world setup wrote into the fog uniform buffer, without applying the fog toggle.
     */
    public static FogState captureRaw() {
        return lastWorldFog;
    }
    
    /**
     * Rebinds the fog slice and the recorded fog values of the view that contains a portal view.
     * Every world render ends with the empty fog bound.
     * The slice itself stays intact because a portal view writes its fog into its own buffer.
     */
    public static void restoreWorldFog(FogState fog, GpuBufferSlice slice) {
        lastWorldFog = fog;
        
        if (slice != null) {
            RenderSystem.setShaderFog(slice);
        }
    }
    
    private static GpuBuffer createFogBuffer(FogState fog) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, FogRenderer.FOG_UBO_SIZE)
                .putVec4(fog.red(), fog.green(), fog.blue(), fog.alpha())
                .putFloat(fog.environmentalStart())
                .putFloat(fog.environmentalEnd())
                .putFloat(fog.renderDistanceStart())
                .putFloat(fog.renderDistanceEnd())
                .putFloat(fog.skyEnd())
                .putFloat(fog.cloudEnd())
                .get();
            return RenderSystem.getDevice().createBuffer(
                () -> "Immersive Portals portal fog", GpuBuffer.USAGE_UNIFORM, data
            );
        }
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
        
        MeshData mesh = ViewAreaRenderer.buildViewAreaMesh(Vec3.ZERO, portal, CHelper.getCurrentCameraPos());
        if (mesh == null) {
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
        
        GpuBuffer fogBuffer = createFogBuffer(fog);
        
        try {
            if (PortalRendering.isRendering()) {
                FrontClipping.setupInnerClipping(
                    PortalRendering.getActiveClippingPlane(), modelView, 0
                );
            }
            else {
                FrontClipping.disableClipping();
            }
            
            CHelper.enableDepthClamp();
            
            PortalDraw.drawMesh(
                "portal fog", PortalPipelines.portalFog(),
                mesh, Minecraft.getInstance().getMainRenderTarget(),
                modelView, null,
                pass -> pass.setUniform("Fog", fogBuffer),
                true, false
            );
        }
        finally {
            CHelper.disableDepthClamp();
            fogBuffer.close();
            GlStateManager._depthFunc(depthFunc);
            GlStateManager._depthMask(depthMask);
            if (depthTest) GlStateManager._enableDepthTest();
            else GlStateManager._disableDepthTest();
            if (cull) GlStateManager._enableCull();
            else GlStateManager._disableCull();
            GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) GlStateManager._enableBlend();
            else GlStateManager._disableBlend();
            FrontClipping.disableClipping();
        }
    }
}
