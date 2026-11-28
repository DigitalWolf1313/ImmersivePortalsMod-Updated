package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.Objects;

/**
 * Bridges the pre-1.21.5 framebuffer-style calls to the GpuTexture-based render targets.
 * Vanilla binds a framebuffer only for the lifetime of a render pass and unbinds it afterwards,
 * so code that mixes raw GL calls with render targets must bind the framebuffer itself.
 */
public class IPGlHelper {
    /**
     * Returns the framebuffer object that has the target's color and depth textures attached.
     * The framebuffer is owned by the color texture and is deleted together with it.
     */
    public static int getFbo(RenderTarget target) {
        GpuTexture color = Objects.requireNonNull(target.getColorTexture(), "no color texture");
        GlDevice device = (GlDevice) RenderSystem.getDevice();
        return ((GlTexture) color).getFbo(device.directStateAccess(), target.getDepthTexture());
    }
    
    public static int getColorTextureId(RenderTarget target) {
        return ((GlTexture) Objects.requireNonNull(target.getColorTexture(), "no color texture")).glId();
    }
    
    public static int glId(GpuTexture texture) {
        return ((GlTexture) Objects.requireNonNull(texture, "no texture")).glId();
    }
    
    public static int getDepthTextureId(RenderTarget target) {
        return ((GlTexture) Objects.requireNonNull(target.getDepthTexture(), "no depth texture")).glId();
    }
    
    /**
     * Binds the target for drawing with raw GL state.
     * The caller is responsible for unbinding when a later vanilla render pass is not going to rebind.
     */
    public static void bindWrite(RenderTarget target, boolean setViewport) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, getFbo(target));
        if (setViewport) {
            GlStateManager._viewport(0, 0, target.viewWidth, target.viewHeight);
        }
    }
    
    public static void unbindWrite() {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }
    
    public static void bindRead(RenderTarget target) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, getFbo(target));
    }
    
    public static void bindDraw(RenderTarget target) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, getFbo(target));
    }
    
    /**
     * Clears the color and depth of the target with the given color.
     * Leaves the target bound as the write framebuffer.
     */
    public static void clear(RenderTarget target, float r, float g, float b, float a, boolean clearDepth) {
        bindWrite(target, true);
        GlStateManager._disableScissorTest();
        GlStateManager._colorMask(true, true, true, true);
        GL11.glClearColor(r, g, b, a);
        int mask = GL11.GL_COLOR_BUFFER_BIT;
        if (clearDepth && target.useDepth) {
            GlStateManager._depthMask(true);
            GL11.glClearDepth(1.0);
            mask |= GL11.GL_DEPTH_BUFFER_BIT;
        }
        GlStateManager._clear(mask);
    }
    
    /**
     * Clears only the alpha channel of the target's color attachment.
     */
    public static void clearAlpha(RenderTarget target, float alpha) {
        bindWrite(target, true);
        GlStateManager._disableScissorTest();
        GlStateManager._colorMask(false, false, false, true);
        GL11.glClearColor(0, 0, 0, alpha);
        GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT);
        GlStateManager._colorMask(true, true, true, true);
    }
}
