package qouteall.imm_ptl.core.compat.iris_compatibility;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.FrontClipping;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

// Adds portal surfaces to the Iris shadow map as opaque geometry.
public class IrisPortalShadowCaster {
    // Lazily initialized 1x1 white texture.
    private static int whiteTexture = 0;
    
    public static void render() {
        if (!IPGlobal.irisPortalShadow) {
            return;
        }
        
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        Frustum frustum = ShadowRenderer.FRUSTUM;
        Matrix4f shadowModelView = ShadowRenderer.MODELVIEW;
        
        if (level == null || frustum == null || shadowModelView == null) {
            return;
        }
        
        List<Portal> portals = collectPortals(level, frustum);
        if (portals.isEmpty()) {
            return;
        }
        
        Vector3d cameraPos = CameraUniforms.getUnshiftedCameraPosition();
        Vec3 cameraVec = new Vec3(cameraPos.x, cameraPos.y, cameraPos.z);
        
        BufferBuilder builder = RenderSystem.renderThreadTesselator()
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
        
        // Vertices are already in shadow view space.
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();
        RenderSystem.applyModelViewMatrix();
        
        // Iris uses the pack's shadow shader here, so bind opaque white for its texture samples.
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderTexture(0, getWhiteTexture());
        RenderSystem.setShaderColor(1, 1, 1, 1);
        
        RenderSystem.disableBlend();
        GlStateManager._enableCull();
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        FrontClipping.disableClipping();
        
        // Keep portal surfaces that cross the orthographic near or far plane.
        CHelper.enableDepthClamp();
        BufferUploader.drawWithShader(mesh);
        CHelper.disableDepthClamp();
        GlStateManager._disableCull();
        
        modelViewStack.popMatrix();
        RenderSystem.applyModelViewMatrix();
        
        // Iris expects culling to remain disabled for the rest of the shadow pass.
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
    
    private static int getWhiteTexture() {
        if (whiteTexture != 0) {
            return whiteTexture;
        }
        
        int[] textures = new int[1];
        GlStateManager._genTextures(textures);
        int texture = textures[0];
        
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF).put((byte) 0xFF);
        pixel.flip();
        
        // Iris binds this texture during upload; restore the active unit's previous binding.
        int previousBinding = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        
        IrisRenderSystem.texImage2D(
            texture, GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, 1, 1, 0,
            GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, pixel
        );
        IrisRenderSystem.texParameteri(
            texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST
        );
        IrisRenderSystem.texParameteri(
            texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST
        );
        
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previousBinding);
        
        whiteTexture = texture;
        return texture;
    }
}
