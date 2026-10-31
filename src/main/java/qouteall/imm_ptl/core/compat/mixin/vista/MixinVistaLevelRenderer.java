package qouteall.imm_ptl.core.compat.mixin.vista;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.compat.IPPortingLibCompat;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisCompatibilityPortalRenderer;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisPortalRenderer;
import qouteall.imm_ptl.core.ducks.IEGameRenderer;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.imm_ptl.core.render.renderer.RendererUsingStencil;

@Pseudo
@Mixin(targets = "net.mehvahdjukaar.vista.client.renderer.VistaLevelRenderer", remap = false)
public abstract class MixinVistaLevelRenderer {
    @Shadow private static ResourceKey<Level> lastLevel;
    @Unique private static boolean ip$renderingIrisFeed;
    
    @WrapMethod(method = "render(Lnet/mehvahdjukaar/vista/client/textures/perspective/PerspectiveTexture;Lnet/mehvahdjukaar/vista/common/view_finder/ViewFinderBlockEntity;)V")
    private static void ip$renderInSourceWorld(@Coerce Object texture, @Coerce Object viewFinder, Operation<Void> original) {
        Minecraft client = Minecraft.getInstance();
        Level source = ((BlockEntity) viewFinder).getLevel();
        if (!(source instanceof ClientLevel world) || client.level == null) {
            original.call(texture, viewFinder);
            return;
        }
        
        ClientLevel oldWorld = client.level;
        LevelRenderer oldRenderer = client.levelRenderer;
        LightTexture oldLight = client.gameRenderer.lightTexture();
        Level oldDispatcherWorld = client.getBlockEntityRenderDispatcher().level;
        boolean switched = world != oldWorld;
        try {
            if (switched) {
                client.level = world;
                ((IEMinecraftClient) client).ip_setWorldRenderer(ClientWorldLoader.getWorldRenderer(world.dimension()));
                ((IEGameRenderer) client.gameRenderer).ip_setLightmapTextureManager(
                    ClientWorldLoader.getDimensionRenderHelper(world.dimension()).lightmapTexture);
                ((IEParticleManager) client.particleEngine).ip_setWorld(world);
                client.getBlockEntityRenderDispatcher().level = world;
            }
            // Vista skips the first render after a dimension change.
            // Feeds in two dimensions would suppress each other without this.
            // It is not restored, as it must describe the world that was just rendered.
            lastLevel = world.dimension();
            original.call(texture, viewFinder);
        }
        finally {
            if (switched) {
                client.level = oldWorld;
                ((IEMinecraftClient) client).ip_setWorldRenderer(oldRenderer);
                ((IEGameRenderer) client.gameRenderer).ip_setLightmapTextureManager(oldLight);
                ((IEParticleManager) client.particleEngine).ip_setWorld(oldWorld);
                client.getBlockEntityRenderDispatcher().level = oldDispatcherWorld;
            }
        }
    }
    
    @WrapOperation(method = "doRender", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V", ordinal = 0, remap = true))
    private static void ip$prepareFeedStencil(RenderTarget target, boolean viewport, Operation<Void> original) {
        if (IPCGlobal.renderer == IPCGlobal.rendererUsingStencil) {
            if (!IPPortingLibCompat.getIsStencilEnabled(target)) {
                IPPortingLibCompat.setIsStencilEnabled(target, true);
            }
            original.call(target, viewport);
            GL11.glClearStencil(0);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        }
        else {
            original.call(target, viewport);
        }
    }
    
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", remap = true))
    private static void ip$useFeedCamera(LevelRenderer renderer, DeltaTracker ticks, boolean outline,
        Camera camera, GameRenderer gameRenderer, LightTexture light, Matrix4f modelView,
        Matrix4f projection, Operation<Void> original) {
        Camera oldCamera = RenderStates.originalCamera;
        Matrix4f oldProjection = RenderStates.basicProjectionMatrix;
        RenderStates.originalCamera = camera;
        RenderStates.basicProjectionMatrix = new Matrix4f(projection);
        
        boolean stencilWasEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        int stencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
        int stencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        int stencilMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
        int stencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
        int stencilDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
        int stencilDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
        
        PortalRenderer portalRenderer = IPCGlobal.renderer;
        PortalRenderer feedRenderer = null;
        if (!ip$renderingIrisFeed && !PortalRendering.isRendering()) {
            if (portalRenderer == IrisPortalRenderer.instance) {
                feedRenderer = IrisPortalRenderer.vistaInstance;
            }
            else if (portalRenderer == IrisCompatibilityPortalRenderer.instance) {
                feedRenderer = IrisCompatibilityPortalRenderer.vistaInstance;
            }
            else if (portalRenderer == IrisCompatibilityPortalRenderer.debugModeInstance) {
                feedRenderer = IrisCompatibilityPortalRenderer.vistaDebugInstance;
            }
        }
        
        try {
            if (feedRenderer != null) {
                ip$renderingIrisFeed = true;
                IPCGlobal.renderer = feedRenderer;
                feedRenderer.prepareRendering();
            }
            if (portalRenderer == IPCGlobal.rendererUsingStencil) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
                RendererUsingStencil.setStencilLimitation(0);
            }
            original.call(renderer, ticks, outline, camera, gameRenderer, light, modelView, projection);
            if (feedRenderer != null) {
                feedRenderer.onBeforeHandRendering(modelView);
                feedRenderer.finishRendering();
            }
        }
        finally {
            if (feedRenderer != null) {
                IPCGlobal.renderer = portalRenderer;
                ip$renderingIrisFeed = false;
            }
            GL11.glStencilFunc(stencilFunc, stencilRef, stencilMask);
            GL11.glStencilOp(stencilFail, stencilDepthFail, stencilDepthPass);
            if (stencilWasEnabled) {
                GL11.glEnable(GL11.GL_STENCIL_TEST);
            }
            else {
                GL11.glDisable(GL11.GL_STENCIL_TEST);
            }
            RenderStates.originalCamera = oldCamera;
            RenderStates.basicProjectionMatrix = oldProjection;
        }
    }
}
