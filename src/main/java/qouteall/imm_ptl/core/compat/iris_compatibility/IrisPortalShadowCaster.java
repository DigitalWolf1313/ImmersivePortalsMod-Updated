package qouteall.imm_ptl.core.compat.iris_compatibility;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.IPRenderSystem;
import qouteall.imm_ptl.core.render.PortalDraw;
import qouteall.imm_ptl.core.render.PortalPipelines;
import qouteall.q_misc_util.Helper;

import java.util.ArrayList;
import java.util.List;

// Adds portal surfaces to the Iris shadow map as opaque geometry.
public class IrisPortalShadowCaster {
    // The render pass needs a color attachment, but the portal surfaces only matter for the shadow depth.
    // This texture takes the color output so the shadow color buffers stay untouched.
    private static GpuTexture dummyColorTexture;
    private static int dummyColorResolution = 0;
    
    private static boolean disabledByError = false;
    
    public static void render() {
        if (!IPGlobal.irisPortalShadow || disabledByError) {
            return;
        }
        
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        Frustum frustum = ShadowRenderer.FRUSTUM;
        Matrix4f shadowModelView = ShadowRenderer.MODELVIEW;
        Matrix4f shadowProjection = ShadowRenderer.PROJECTION;
        
        if (level == null || frustum == null || shadowModelView == null || shadowProjection == null) {
            return;
        }
        
        if (!(IrisInterface.invoker.getCurrentPipeline() instanceof IEIrisNewWorldRenderingPipeline pipeline)) {
            return;
        }
        ShadowRenderTargets targets = pipeline.ip_getShadowRenderTargets();
        if (targets == null) {
            return;
        }
        
        List<Portal> portals = collectPortals(level, frustum);
        if (portals.isEmpty()) {
            return;
        }
        
        Vector3d cameraPos = CameraUniforms.getUnshiftedCameraPosition();
        Vec3 cameraVec = new Vec3(cameraPos.x, cameraPos.y, cameraPos.z);
        
        BufferBuilder builder = Tesselator.getInstance()
            .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        
        Vector3f transformed = new Vector3f();
        
        for (Portal portal : portals) {
            portal.renderViewAreaMesh(
                portal.getOriginPos().subtract(cameraVec),
                (x0, y0, z0, x1, y1, z1, x2, y2, z2) -> {
                    emitVertex(builder, shadowModelView, transformed, x0, y0, z0);
                    emitVertex(builder, shadowModelView, transformed, x1, y1, z1);
                    emitVertex(builder, shadowModelView, transformed, x2, y2, z2);
                }
            );
        }
        
        MeshData mesh = builder.build();
        if (mesh == null) {
            return;
        }
        
        // The render pass binds its own framebuffer, so the Iris shadow framebuffer is rebound afterwards.
        int previousFramebuffer = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        
        GpuDevice device = RenderSystem.getDevice();
        GpuTextureView colorView = null;
        GpuTextureView depthView = null;
        
        try {
            colorView = device.createTextureView(getDummyColorTexture(device, targets.getResolution()));
            depthView = device.createTextureView(targets.getDepthTexture());
            
            // Keep portal surfaces that cross the orthographic near or far plane.
            CHelper.enableDepthClamp();
            
            // Vertices are already in shadow view space.
            PortalDraw.drawMeshToViews(
                "iris shadow", PortalPipelines.portalArea(true, true, true),
                mesh, colorView, depthView,
                new Matrix4f(), IPRenderSystem.uploadProjectionMatrix(shadowProjection),
                null, false, false,
                () -> GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, previousFramebuffer)
            );
        }
        catch (RuntimeException e) {
            disabledByError = true;
            Helper.LOGGER.error("[iPortal] Portal shadow casting failed and is disabled", e);
        }
        finally {
            CHelper.disableDepthClamp();
            if (colorView != null) {
                colorView.close();
            }
            if (depthView != null) {
                depthView.close();
            }
        }
        
        // Iris expects culling to remain disabled for the rest of the shadow pass.
        GlStateManager._disableCull();
    }
    
    private static GpuTexture getDummyColorTexture(GpuDevice device, int resolution) {
        if (dummyColorTexture == null || dummyColorResolution != resolution) {
            if (dummyColorTexture != null) {
                dummyColorTexture.close();
            }
            dummyColorTexture = device.createTexture(
                "ip_iris_shadow_dummy_color", GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.RGBA8, resolution, resolution, 1, 1
            );
            dummyColorResolution = resolution;
        }
        return dummyColorTexture;
    }
    
    private static List<Portal> collectPortals(ClientLevel level, Frustum frustum) {
        List<Portal> portals = new ArrayList<>();
        
        // Global portals are excluded because they are not entities.
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof Portal portal)) {
                continue;
            }
            
            if (!portal.isPortalValid() || !portal.isVisible() || portal.isFuseView()
                || !portal.getRenderIrisShadow()) {
                continue;
            }
            
            if (!frustum.isVisible(portal.getThinBoundingBox())) {
                continue;
            }
            
            portals.add(portal);
        }
        
        return portals;
    }
    
    private static void emitVertex(
        BufferBuilder builder, Matrix4f modelView, Vector3f temp,
        double x, double y, double z
    ) {
        modelView.transformPosition((float) x, (float) y, (float) z, temp);
        builder.addVertex(temp.x, temp.y, temp.z).setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
