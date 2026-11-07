package qouteall.imm_ptl.core.compat;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.config.EDhApiMcRenderingFadeMode;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig;
import com.seibel.distanthorizons.common.render.openGl.GlDhMetaRenderer;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.level.ClientLevelModule;
import com.seibel.distanthorizons.core.level.DhClientLevel;
import com.seibel.distanthorizons.core.level.DhClientServerLevel;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.q_misc_util.Helper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the Distant Horizons fade input intact while portal views render.
 * Core rendering code reaches Distant Horizons only through this class,
 * so it stays safe to load when Distant Horizons is absent.
 * <p>
 * The active meta framebuffer and {@code vanillaFadeMode} are read from Distant Horizons internals,
 * because the API does not expose them.
 */
public class IPDistantHorizonsCompat {
    
    public static boolean isDHPresent = false;
    
    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("distanthorizons")) {
            Helper.logger.info("Distant Horizons is present");
            isDHPresent = true;
        }
    }
    
    private static final long TICK_INTERVAL_NANOS = 50_000_000L;
    private static final long OWNER_TAKEOVER_NANOS = 500_000_000L;
    private static final Map<ResourceKey<Level>, LevelState> levelStates = new HashMap<>();
    private static boolean quadTreeTickingFailed = false;
    private static boolean loggedFirstQuadTreeTick = false;
    
    private static class LevelState {
        @Nullable List<UUID> owner;
        long ownerLastSeenNanos;
        long lastTickNanos;
    }
    
    private static int backupFramebuffer = -1;
    private static int colorRenderbuffer = -1;
    private static int depthRenderbuffer = -1;
    private static int backupWidth = 0;
    private static int backupHeight = 0;
    
    // The frame whose fade input the backup holds.
    private static int backupFrameIndex = -1;
    private static boolean hasBackup = false;
    private static boolean failed = false;
    private static boolean loggedFirstUse = false;
    
    /**
     * Must be called on the render thread while a portal view renders,
     * before Distant Horizons renders that view.
     * Does nothing when the rendered level is the player's own level.
     */
    public static void tickRenderedLevel() {
        if (quadTreeTickingFailed || !WorldRenderInfo.isRendering()) {
            return;
        }
        
        Minecraft client = Minecraft.getInstance();
        ClientLevel renderedLevel = client.level;
        if (client.player == null || renderedLevel == null || renderedLevel == client.player.level()) {
            return;
        }
        
        try {
            tickLevel(renderedLevel);
        }
        catch (Throwable e) {
            quadTreeTickingFailed = true;
            Helper.logger.error("Distant Horizons portal quad tree ticking failed and is disabled", e);
        }
    }
    
    private static void tickLevel(ClientLevel level) {
        ClientLevelModule clientside = getClientLevelModule(level);
        if (clientside == null) {
            return;
        }
        
        ClientLevelModule.ClientRenderState renderState = clientside.ClientRenderStateRef.get();
        if (renderState == null) {
            return;
        }
        
        long now = System.nanoTime();
        LevelState state = levelStates.computeIfAbsent(level.dimension(), k -> new LevelState());
        
        List<UUID> view = WorldRenderInfo.getRenderingDescription();
        if (!view.equals(state.owner)) {
            if (state.owner != null && now - state.ownerLastSeenNanos < OWNER_TAKEOVER_NANOS) {
                return;
            }
            
            Helper.logger.info(
                "Distant Horizons quad tree of {} is now driven by portal view {}",
                level.dimension().location(), view
            );
            state.owner = view;
            
            // System.nanoTime has an arbitrary origin, so the first tick cannot compare against zero.
            state.lastTickNanos = now - TICK_INTERVAL_NANOS;
        }
        state.ownerLastSeenNanos = now;
        
        if (now - state.lastTickNanos < TICK_INTERVAL_NANOS) {
            return;
        }
        state.lastTickNanos = now;
        
        Vec3 cameraPos = WorldRenderInfo.getCameraPos();
        int blockX = Mth.floor(cameraPos.x);
        int blockZ = Mth.floor(cameraPos.z);
        
        if (!loggedFirstQuadTreeTick) {
            loggedFirstQuadTreeTick = true;
            Helper.logger.debug(
                "Ticking Distant Horizons quad tree of {} at ({}, {})",
                level.dimension().location(), blockX, blockZ
            );
        }
        renderState.quadtree.tryTick(new DhBlockPos2D(blockX, blockZ));
    }
    
    @Nullable
    private static ClientLevelModule getClientLevelModule(ClientLevel level) {
        IClientLevelWrapper wrapper = ClientLevelWrapper.getWrapper(level);
        if (wrapper == null) {
            return null;
        }
        
        IDhLevel dhLevel = wrapper.getDhLevel();
        if (dhLevel instanceof DhClientLevel clientLevel) {
            return clientLevel.clientside;
        }
        if (dhLevel instanceof DhClientServerLevel clientServerLevel) {
            return clientServerLevel.clientside;
        }
        return null;
    }
    
    /**
     * Backs up the fade input before an outermost portal view renders.
     */
    public static void beforeOuterPortalView() {
        if (isDHPresent) {
            save();
        }
    }
    
    /**
     * Restores the fade input after an outermost portal view has rendered.
     */
    public static void afterOuterPortalView() {
        if (isDHPresent) {
            restore();
        }
    }
    
    private static void save() {
        hasBackup = false;
        
        if (failed || !isFadeInputNeeded()) {
            return;
        }
        
        // Every restore writes the same contents back,
        // so a backup taken for one portal view stays valid for the other views of the frame.
        if (backupFrameIndex == RenderStates.frameIndex) {
            hasBackup = true;
            return;
        }
        
        int dhFramebuffer = GlDhMetaRenderer.INSTANCE.getActiveFramebufferId();
        if (dhFramebuffer <= 0) {
            return;
        }
        
        var mainTarget = Minecraft.getInstance().getMainRenderTarget();
        int width = mainTarget.viewWidth;
        int height = mainTarget.viewHeight;
        
        runPreservingGlState(() -> {
            ensureBackupBuffers(width, height);
            blit(dhFramebuffer, backupFramebuffer, width, height);
        });
        
        if (!failed) {
            hasBackup = true;
            backupFrameIndex = RenderStates.frameIndex;
            
            if (!loggedFirstUse) {
                loggedFirstUse = true;
                Helper.logger.info("Distant Horizons fade input backup is active ({}x{})", width, height);
            }
        }
    }
    
    private static void restore() {
        if (!hasBackup) {
            return;
        }
        hasBackup = false;
        
        int dhFramebuffer = GlDhMetaRenderer.INSTANCE.getActiveFramebufferId();
        if (dhFramebuffer <= 0) {
            return;
        }
        
        runPreservingGlState(() -> blit(backupFramebuffer, dhFramebuffer, backupWidth, backupHeight));
    }
    
    private static boolean isFadeInputNeeded() {
        IDhApiConfig configs = DhApi.Delayed.configs;
        if (configs == null) {
            // Distant Horizons has not finished initializing, so it is not rendering LODs.
            return false;
        }
        
        if (!configs.graphics().renderingEnabled().getValue()) {
            return false;
        }
        
        boolean fadeEnabled =
            Config.Client.Advanced.Graphics.Quality.vanillaFadeMode.get() != EDhApiMcRenderingFadeMode.NONE
                || configs.debugging().lodOnlyMode().getValue();
        if (!fadeEnabled) {
            return false;
        }
        
        // Fading is not used while a shader pack is active.
        return !IrisInterface.invoker.isShaders();
    }
    
    private static void ensureBackupBuffers(int width, int height) {
        if (backupFramebuffer == -1) {
            backupFramebuffer = GL30.glGenFramebuffers();
            colorRenderbuffer = GL30.glGenRenderbuffers();
            depthRenderbuffer = GL30.glGenRenderbuffers();
            backupWidth = 0;
            backupHeight = 0;
        }
        
        if (backupWidth == width && backupHeight == height) {
            return;
        }
        
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, colorRenderbuffer);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_RGBA8, width, height);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRenderbuffer);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT32F, width, height);
        
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, backupFramebuffer);
        GL30.glFramebufferRenderbuffer(
            GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_RENDERBUFFER, colorRenderbuffer
        );
        GL30.glFramebufferRenderbuffer(
            GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depthRenderbuffer
        );
        
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Backup framebuffer is incomplete: " + status);
        }
        
        backupWidth = width;
        backupHeight = height;
        
        // The old contents are gone after reallocating the storage.
        backupFrameIndex = -1;
    }
    
    private static void blit(int readFramebuffer, int drawFramebuffer, int width, int height) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        
        GL30.glBlitFramebuffer(
            0, 0, width, height,
            0, 0, width, height,
            GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
            GL11.GL_NEAREST
        );
    }
    
    private static void runPreservingGlState(Runnable action) {
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousRenderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        boolean scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        
        try {
            action.run();
        }
        catch (Throwable e) {
            failed = true;
            Helper.logger.error("Distant Horizons fade input backup failed and is disabled", e);
        }
        finally {
            if (scissorWasEnabled) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        }
    }
}
