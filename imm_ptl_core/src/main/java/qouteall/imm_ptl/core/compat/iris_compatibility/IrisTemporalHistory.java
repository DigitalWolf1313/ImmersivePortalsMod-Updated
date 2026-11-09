package qouteall.imm_ptl.core.compat.iris_compatibility;

import com.mojang.blaze3d.platform.GlStateManager;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.texture.PixelType;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the retained colortex contents of every view rendered through one Iris pipeline.
 * Only the main texture of each target is kept.
 * Iris copies the final data of flipped targets back to main at the end of the frame,
 * and the alt texture is always written before it is read.
 */
public final class IrisTemporalHistory {
    private record ViewKey(ResourceKey<Level> dimension, List<UUID> path) {}
    
    /** The saved main texture of one Iris render target. */
    private record Backup(int texture, int width, int height, InternalTextureFormat format) {
        void destroy() {
            GlStateManager._deleteTexture(texture);
        }
    }
    
    private static final class View {
        final IrisTemporalViewState camera = new IrisTemporalViewState();
        final Map<Integer, Backup> textures = new HashMap<>();
        int lastFrame;
        boolean valid;
        
        void destroy() {
            textures.values().forEach(Backup::destroy);
            textures.clear();
            valid = false;
        }
    }
    
    private final Map<ViewKey, View> views = new HashMap<>();
    private View active;
    private int readFramebuffer;
    private int drawFramebuffer;
    
    public void begin() {
        int frame = RenderStates.frameIndex;
        // A view absent for a full frame has no consecutive history. Release its VRAM promptly.
        views.values().removeIf(view -> {
            if (frame - view.lastFrame > 1) {
                view.destroy();
                return true;
            }
            return false;
        });
        Minecraft client = Minecraft.getInstance();
        ViewKey key = new ViewKey(client.level.dimension(),
            Collections.unmodifiableList(new ArrayList<>(WorldRenderInfo.getRenderingDescription())));
        active = views.computeIfAbsent(key, ignored -> new View());
        var target = client.getMainRenderTarget();
        var captured = CapturedRenderingState.INSTANCE;
        boolean continuous = active.camera.begin(frame, CameraUniforms.getUnshiftedCameraPosition(),
            captured.getGbufferModelView(), captured.getGbufferProjection(),
            target.width, target.height, ClientTeleportationManager.isTeleportingFrame);
        active.valid &= continuous;
        active.lastFrame = frame;
        IrisTemporalContext.activate(active.camera);
    }
    
    /**
     * Restores the saved textures after Iris resizes its targets and before its clear passes.
     * Returns true when the history was restored.
     * Returns false when the history was discarded.
     */
    public boolean restore(RenderTargets targets, boolean fullClear) {
        if (fullClear || !active.valid || !matches(targets)) {
            active.destroy();
            return false;
        }
        copy(targets, false);
        return true;
    }
    
    /**
     * Saves the retained textures.
     * This must run after Iris's final pass, which copies flipped targets back to main.
     */
    public void save(RenderTargets targets, PackDirectives directives) {
        var cleared = directives.getRenderTargetDirectives().getBuffersToBeCleared();
        int binding = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        try {
            for (int i = 0; i < targets.getRenderTargetCount(); i++) {
                RenderTarget source = targets.get(i);
                if (source == null || cleared.contains(i)) continue;
                Backup backup = active.textures.get(i);
                if (backup != null && !fits(source, backup)) {
                    backup.destroy();
                    backup = null;
                }
                if (backup == null) {
                    active.textures.put(i, createBackup(i, source));
                }
            }
        }
        finally {
            GlStateManager._bindTexture(binding);
        }
        copy(targets, true);
        active.valid = true;
    }
    
    private static Backup createBackup(int index, RenderTarget source) {
        InternalTextureFormat format = source.getInternalFormat();
        int texture = GlStateManager._genTexture();
        IrisRenderSystem.texImage2D(texture, GL11C.GL_TEXTURE_2D, 0, format.getGlFormat(),
            source.getWidth(), source.getHeight(), 0, format.getPixelFormat().getGlFormat(),
            PixelType.UNSIGNED_BYTE.getGlFormat(), null);
        
        // The default minification filter needs mipmaps, which would make the texture incomplete for copying.
        int filter = format.getPixelFormat().isInteger() ? GL11C.GL_NEAREST : GL11C.GL_LINEAR;
        IrisRenderSystem.texParameteri(texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, filter);
        IrisRenderSystem.texParameteri(texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, filter);
        
        GLDebug.nameObject(GL43C.GL_TEXTURE, texture, "ip_history_" + index);
        return new Backup(texture, source.getWidth(), source.getHeight(), format);
    }
    
    private boolean matches(RenderTargets targets) {
        for (var entry : active.textures.entrySet()) {
            RenderTarget target = targets.get(entry.getKey());
            if (target == null || !fits(target, entry.getValue())) return false;
        }
        return true;
    }
    
    private static boolean fits(RenderTarget target, Backup backup) {
        return target.getWidth() == backup.width() && target.getHeight() == backup.height()
            && target.getInternalFormat() == backup.format();
    }
    
    private void copy(RenderTargets targets, boolean saving) {
        if (active.textures.isEmpty()) return;
        if (IPIrisHelper.isCopyImageSubDataSupported()) {
            // This path ignores the framebuffer, scissor and sRGB state, so none of it is saved or restored.
            for (var entry : active.textures.entrySet()) {
                int live = targets.get(entry.getKey()).getMainTexture();
                Backup backup = entry.getValue();
                int from = saving ? live : backup.texture();
                int to = saving ? backup.texture() : live;
                IPIrisHelper.copyTexture2D(from, to, backup.width(), backup.height());
            }
            return;
        }
        copyWithBlit(targets, saving);
    }
    
    private void copyWithBlit(RenderTargets targets, boolean saving) {
        if (readFramebuffer == 0) {
            readFramebuffer = GL30C.glGenFramebuffers();
            drawFramebuffer = GL30C.glGenFramebuffers();
        }
        int oldRead = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int oldDraw = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        boolean scissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        boolean srgb = GL11C.glIsEnabled(GL30C.GL_FRAMEBUFFER_SRGB);
        try {
            // Blitting works on Iris's OpenGL 3.3 baseline, including integer attachments.
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
            GL11C.glDisable(GL30C.GL_FRAMEBUFFER_SRGB);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL11C.glReadBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            GL11C.glDrawBuffer(GL30C.GL_COLOR_ATTACHMENT0);
            for (var entry : active.textures.entrySet()) {
                int live = targets.get(entry.getKey()).getMainTexture();
                Backup backup = entry.getValue();
                int from = saving ? live : backup.texture();
                int to = saving ? backup.texture() : live;
                blitTexture(from, to, backup.width(), backup.height());
            }
        }
        finally {
            // Do not keep evicted textures alive through framebuffer attachments.
            GL30C.glFramebufferTexture2D(GL30C.GL_READ_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                GL11C.GL_TEXTURE_2D, 0, 0);
            GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                GL11C.GL_TEXTURE_2D, 0, 0);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, oldRead);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, oldDraw);
            if (scissor) GL11C.glEnable(GL11C.GL_SCISSOR_TEST);
            if (srgb) GL11C.glEnable(GL30C.GL_FRAMEBUFFER_SRGB);
        }
    }
    
    private static void blitTexture(int from, int to, int width, int height) {
        GL30C.glFramebufferTexture2D(GL30C.GL_READ_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
            GL11C.GL_TEXTURE_2D, from, 0);
        GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
            GL11C.GL_TEXTURE_2D, to, 0);
        GL30C.glBlitFramebuffer(0, 0, width, height,
            0, 0, width, height, GL11C.GL_COLOR_BUFFER_BIT, GL11C.GL_NEAREST);
    }
    
    public void destroy() {
        for (View view : views.values()) {
            if (IrisTemporalContext.current() == view.camera) IrisTemporalContext.activate(null);
            view.destroy();
        }
        views.clear();
        active = null;
        if (readFramebuffer != 0) GL30C.glDeleteFramebuffers(readFramebuffer);
        if (drawFramebuffer != 0) GL30C.glDeleteFramebuffers(drawFramebuffer);
        readFramebuffer = drawFramebuffer = 0;
    }
}
