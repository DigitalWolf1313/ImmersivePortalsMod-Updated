package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;
import qouteall.imm_ptl.core.render.FrontClipping;
import qouteall.imm_ptl.core.render.ImmPtlViewArea;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.OverlayRendering;
import qouteall.imm_ptl.core.render.VisibleSectionDiscovery;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.Helper;

@SuppressWarnings("JavadocReference")
@Mixin(value = LevelRenderer.class)
public abstract class MixinLevelRenderer implements IEWorldRenderer {
    
    @Shadow
    private ClientLevel level;
    
    @Shadow
    @Final
    private EntityRenderDispatcher entityRenderDispatcher;
    
    @Shadow
    @Final
    private Minecraft minecraft;
    
    @Shadow
    private ViewArea viewArea;
    
    @Shadow
    protected abstract void renderEntity(
        Entity entity_1,
        double double_1,
        double double_2,
        double double_3,
        float float_1,
        PoseStack matrixStack_1,
        MultiBufferSource vertexConsumerProvider_1
    );
    
    @Mutable
    @Shadow
    @Final
    private RenderBuffers renderBuffers;
    
    @Shadow
    private int lastViewDistance;
    
    @Shadow
    private Frustum cullingFrustum;
    
    @Shadow
    private @Nullable SectionRenderDispatcher sectionRenderDispatcher;
    
    @Shadow
    @Final
    @Mutable
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;
    
    @Shadow @Final private static Logger LOGGER;
    
    @Shadow private @Nullable RenderTarget entityOutlineTarget;
    
    @Inject(
        method = "method_62214", // the lambda in addMainPass
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/Camera;Lnet/minecraft/client/DeltaTracker;Ljava/util/List;)V"
        )
    )
    private void onAfterCutoutRendering(CallbackInfo ci, @Local(argsOnly = true) Matrix4f modelView) {
//        IPCGlobal.renderer.onBeforeTranslucentRendering(matrices);
        
        CrossPortalEntityRenderer.onBeginRenderingEntitiesAndBlockEntities(modelView);
    }
    
    @Inject(
        method = "method_62214", // the lambda in addMainPass
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Sheets;translucentItemSheet()Lnet/minecraft/client/renderer/RenderType;"
        )
    )
    private void onMyBeforeTranslucentRendering(CallbackInfo ci, @Local(argsOnly = true) Matrix4f modelView, @Local(argsOnly = true) GpuBufferSlice shaderFog) {
        if (IrisInterface.invoker.isIrisPresent() && !IrisInterface.invoker.isShaders()) {
            // Iris buffers every entity draw until just before the translucent layer.
            // Without flushing here the entities of this world would be drawn on top of the portal views.
            // The portal clipping must be active while flushing, as it is when entities are submitted.
            if (PortalRendering.isRendering()) {
                FrontClipping.setupInnerClipping(
                    PortalRendering.getActiveClippingPlane(), modelView, 0
                );
            }
            renderBuffers.bufferSource().endBatch();
            FrontClipping.disableClipping();
        }
        
        IPCGlobal.renderer.onBeforeTranslucentRendering(modelView);
        
        RenderSystem.setShaderFog(shaderFog);
        
        MyGameRenderer.resetDiffuseLighting();
        
        FrontClipping.disableClipping();
        
        OverlayRendering.flushPendingOverlays();
    }

    @WrapOperation(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;getTransparencyChain()Lnet/minecraft/client/renderer/PostChain;"
        )
    )
    private PostChain skipTransparencyChainDuringPortalRendering(
        LevelRenderer instance, Operation<PostChain> original
    ) {
        if (PortalRendering.isRendering()) {
            return null;
        }
        return original.call(instance);
    }
    
    @IPVanillaCopy
    @Inject(
        method = "method_62214", // the lambda in addMainPass
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V",
            ordinal = 1, // the second occurrence
            shift = At.Shift.AFTER
        )
    )
    private void onEndRenderingEntities(CallbackInfo ci, @Local PoseStack poseStack) {
        CrossPortalEntityRenderer.onEndRenderingEntitiesAndBlockEntities(poseStack);
    }
    
    @Inject(
        method = "renderLevel",
        at = @At("RETURN")
    )
    private void onFinishRenderLevel(CallbackInfo ci) {
        // make hand rendering normal
        MyGameRenderer.resetDiffuseLighting();
    }
    
    @Inject(
        method = "method_62214", // the lambda in addMainPass
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;)V"
        )
    )
    private void onBeforeRenderingLayer(CallbackInfo ci, @Local(argsOnly = true) Matrix4f modelView) {
        if (PortalRendering.isRendering()) {
            FrontClipping.setupInnerClipping(
                PortalRendering.getActiveClippingPlane(),
                modelView,
                -FrontClipping.ADJUSTMENT
                // move the clipping plane a little back, to make world wrapping portal not z-fight
            );
            
            if (PortalRendering.isRenderingOddNumberOfMirrors()) {
                MyRenderHelper.applyMirrorFaceCulling();
            }
            
            if (IPGlobal.enableDepthClampForPortalRendering) {
                CHelper.enableDepthClamp();
            }
        }
        
        FrontClipping.isRenderingTerrain = true;
    }
    
    @Inject(
        method = "method_62214", // the lambda in addMainPass
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;)V",
            shift = At.Shift.AFTER
        )
    )
    private void onAfterRenderingLayer(CallbackInfo ci) {
        FrontClipping.isRenderingTerrain = false;
        
        if (PortalRendering.isRendering()) {
            FrontClipping.disableClipping();
            MyRenderHelper.recoverFaceCulling();
            
            if (IPGlobal.enableDepthClampForPortalRendering) {
                CHelper.disableDepthClamp();
            }
        }
    }
    
    @Inject(
        method = "Lnet/minecraft/client/renderer/LevelRenderer;setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onSetupTerrainBegin(
        Camera camera, Frustum frustum, boolean hasForcedFrustum, boolean spectator,
        CallbackInfo ci
    ) {
        if (WorldRenderInfo.isRendering()) {
            if (level.dimension() != RenderStates.originalPlayerDimension) {
                sectionRenderDispatcher.setCameraPosition(camera.getPosition());
            }
        }
        
        if (ip_allowOverrideTerrainSetup()) {
            if (WorldRenderInfo.isRendering()) {
                Profiler.get().push("ip_terrain_setup");
                VisibleSectionDiscovery.discoverVisibleSections(
                    level, ((ImmPtlViewArea) viewArea),
                    camera,
                    new Frustum(frustum).offsetToFullyIncludeCameraCube(8),
                    visibleSections
                );
                Profiler.get().pop();
                
                ci.cancel();
            }
        }
    }
    
    private boolean ip_allowOverrideTerrainSetup() {
        return !SodiumInterface.invoker.isSodiumPresent()
            && !IrisInterface.invoker.isRenderingShadowMap();
    }
    
    @Inject(
        method = "Lnet/minecraft/client/renderer/LevelRenderer;setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
        at = @At("RETURN"),
        cancellable = true
    )
    private void onSetupTerrainEnd(
        Camera camera, Frustum frustum, boolean hasForcedFrustum, boolean spectator,
        CallbackInfo ci
    ) {
        if (!WorldRenderInfo.isRendering()) {
            if (ip_allowOverrideTerrainSetup()) {
                if (MyGameRenderer.vanillaTerrainSetupOverride > 0) {
                    MyGameRenderer.vanillaTerrainSetupOverride--;
                    
                    Profiler.get().push("ip_terrain_setup");
                    VisibleSectionDiscovery.discoverVisibleSections(
                        level, ((ImmPtlViewArea) viewArea),
                        camera,
                        new Frustum(frustum).offsetToFullyIncludeCameraCube(8),
                        visibleSections
                    );
                    Profiler.get().pop();
                }
                else if (IPGlobal.alwaysOverrideTerrainSetup) {
                    // debug
                    Profiler.get().push("ip_terrain_setup_debug");
                    VisibleSectionDiscovery.discoverVisibleSections(
                        level, ((ImmPtlViewArea) viewArea),
                        camera,
                        new Frustum(frustum).offsetToFullyIncludeCameraCube(8),
                        visibleSections
                    );
                    Profiler.get().pop();
                }
            }
        }
    }
    
    @WrapOperation(
        method = "method_62218", // lambda in renderLevel that clears the main target
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;ILcom/mojang/blaze3d/textures/GpuTexture;D)V"
        )
    )
    private void redirectClearing(
        CommandEncoder encoder, GpuTexture color, int clearColor, GpuTexture depth, double clearDepth,
        Operation<Void> original
    ) {
        if (!IPCGlobal.renderer.replaceFrameBufferClearing()) {
            original.call(encoder, color, clearColor, depth, clearDepth);
        }
    }
    
    @Redirect(
        method = "allChanged",
        at = @At(
            value = "NEW",
            target = "(Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;Lnet/minecraft/world/level/Level;ILnet/minecraft/client/renderer/LevelRenderer;)Lnet/minecraft/client/renderer/ViewArea;"
        )
    )
    private ViewArea redirectConstructingBuildChunkStorage(
        SectionRenderDispatcher chunkBuilder_1,
        Level world_1,
        int int_1,
        LevelRenderer worldRenderer_1
    ) {
        if (IPCGlobal.useHackedChunkRenderDispatcher) {
            return new ImmPtlViewArea(
                chunkBuilder_1, world_1, int_1, worldRenderer_1
            );
        }
        else {
            return new ViewArea(
                chunkBuilder_1, world_1, int_1, worldRenderer_1
            );
        }
    }
    
    @WrapOperation(
        method = "renderEntities",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"
        )
    )
    private void redirectRenderEntity(
        LevelRenderer instance, Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, Operation<Void> original
    ) {
        CrossPortalEntityRenderer.beforeRenderingEntity(entity, poseStack);
        try {
            original.call(
                instance, entity, camX, camY, camZ, partialTick, poseStack, bufferSource
            );
        }
        finally {
            CrossPortalEntityRenderer.afterRenderingEntity(entity);
        }
    }
    
    @Inject(
        method = "method_62216", // lambda in addWeatherPass
        at = @At("HEAD")
    )
    private void beforeRenderingWeather(CallbackInfo ci) {
        if (PortalRendering.isRendering()) {
            FrontClipping.setupInnerClipping(
                PortalRendering.getActiveClippingPlane(),
                RenderSystem.getModelViewMatrix(), 0
            );
            RenderStates.isRenderingPortalWeather = true;
        }
    }
    
    @Inject(
        method = "method_62216", // lambda in addWeatherPass
        at = @At("RETURN")
    )
    private void afterRenderingWeather(CallbackInfo ci) {
        if (PortalRendering.isRendering()) {
            FrontClipping.disableClipping();
            RenderStates.isRenderingPortalWeather = false;
        }
    }
    
    // sometimes we change renderDistance but we don't want to reload it
    @Inject(method = "allChanged", at = @At("HEAD"), cancellable = true)
    private void onReloadStarted(CallbackInfo ci) {
        if (WorldRenderInfo.isRendering()) {
            LOGGER.info("world renderer reloading cancelled during portal rendering");
            ci.cancel();
        }
    }
    
    //reload other world renderers when the main world renderer is reloaded
    @Inject(method = "allChanged", at = @At("TAIL"))
    private void onReloadFinished(CallbackInfo ci) {
        LevelRenderer this_ = (LevelRenderer) (Object) this;
        
        if (ClientWorldLoader.getIsCreatingClientWorld()) {
            return;
        }
        
        Validate.isTrue(Minecraft.getInstance().levelRenderer == this_);
        
        ClientWorldLoader._onWorldRendererReloaded();
    }
    
    @WrapOperation(
        method = "addSkyPass",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/framegraph/FramePass;executes(Ljava/lang/Runnable;)V"
        )
    )
    private void wrapAddSkyPassExecute(FramePass instance, Runnable runnable, Operation<Void> original) {
        original.call(instance, (Runnable) () -> {
            if (WorldRenderInfo.isRendering()) {
                if (!WorldRenderInfo.getTopRenderInfo().doRenderSky) {
                    if (!IrisInterface.invoker.isShaders()) {
                        // skip sky rendering (except for iris)
                        return;
                    }
                }
            }
            
            if (PortalRendering.isRenderingOddNumberOfMirrors()) {
                MyRenderHelper.applyMirrorFaceCulling();
            }
            
            runnable.run();
            
            MyRenderHelper.recoverFaceCulling();
        });
    }
    
//    // vanilla clears translucentFramebuffer even when transparencyShader is null
//    // it makes the framebuffer to be wrongly bound in fabulous mode
//    @Redirect(
//        method = "renderLevel",
//        at = @At(
//            value = "FIELD",
//            target = "Lnet/minecraft/client/renderer/LevelRenderer;translucentTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"
//        )
//    )
//    private RenderTarget redirectTranslucentFramebuffer(LevelRenderer this_) {
//        if (PortalRendering.isRendering()) {
//            return null;
//        }
//        else {
//            return translucentTarget;
//        }
//    }
    
    // if not in spectator mode, when the camera is in block chunk culling will cull chunks wrongly
    @ModifyVariable(
        method = "Lnet/minecraft/client/renderer/LevelRenderer;setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 1
    )
    private boolean modifyIsSpectator(boolean value) {
        if (WorldRenderInfo.isRendering()) {
            return true;
        }
        return value;
    }
    
    // the captured lambda uses the net handler's world field
    // so switch that correctly
    @Redirect(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;pollLightUpdates()V"
        )
    )
    private void redirectRunQueuedChunkUpdates(ClientLevel world) {
        ClientWorldLoader.withSwitchedWorld(
            world, world::pollLightUpdates
        );
    }
    
    /**
     * when rendering portal, it won't call {@link ViewArea#repositionCamera}
     * So {@link ViewArea#getRenderSectionAt} will return incorrect result
     */
    @Inject(
        method = "isSectionCompiled",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onIsChunkCompiled(BlockPos blockPos, CallbackInfoReturnable<Boolean> cir) {
        if (PortalRendering.isRendering()) {
            if (!SodiumInterface.invoker.isSodiumPresent()) {
                if (viewArea instanceof ImmPtlViewArea immPtlViewArea) {
                    cir.setReturnValue(ip_isChunkCompiled(immPtlViewArea, blockPos));
                }
            }
        }
    }
    
    private boolean ip_isChunkCompiled(ImmPtlViewArea immPtlViewArea, BlockPos blockPos) {
        SectionPos sectionPos = SectionPos.of(blockPos);
        var renderChunk = immPtlViewArea.rawGet(
            sectionPos.x(), sectionPos.y(), sectionPos.z()
        );
        
        return renderChunk != null
            && renderChunk.getSectionMesh() != CompiledSectionMesh.UNCOMPILED;
    }
    
    @Override
    public EntityRenderDispatcher ip_getEntityRenderDispatcher() {
        return entityRenderDispatcher;
    }
    
    @Override
    public ViewArea ip_getBuiltChunkStorage() {
        return viewArea;
    }
    
    @Override
    public void ip_myRenderEntity(
        Entity entity,
        double cameraX,
        double cameraY,
        double cameraZ,
        float partialTick,
        PoseStack matrixStack,
        MultiBufferSource vertexConsumerProvider
    ) {
        renderEntity(
            entity, cameraX, cameraY, cameraZ, partialTick, matrixStack, vertexConsumerProvider
        );
    }
    
    @Override
    public RenderBuffers ip_getRenderBuffers() {
        return renderBuffers;
    }
    
    @Override
    public void ip_setRenderBuffers(RenderBuffers arg) {
        renderBuffers = arg;
    }
    
    @Override
    public Frustum portal_getFrustum() {
        return cullingFrustum;
    }
    
    @Override
    public void portal_setFrustum(Frustum arg) {
        cullingFrustum = arg;
    }
    
    @Override
    public void portal_fullyDispose() {
        // this is probably not needed in 1.21.3
    }
    
    @Override
    public void portal_setChunkInfoList(ObjectArrayList<SectionRenderDispatcher.RenderSection> arg) {
        visibleSections = arg;
    }
    
    @Override
    public ObjectArrayList<SectionRenderDispatcher.RenderSection> portal_getChunkInfoList() {
        return visibleSections;
    }
    
    // The sky color is sampled at the portal destination so that it matches the fog color.
    @WrapOperation(
        method = "method_62215", // the lambda in addSkyPass
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getSkyColor(Lnet/minecraft/world/phys/Vec3;F)I"
        )
    )
    private int ip_wrapSkyColorInSkyPass(
        ClientLevel clientLevel, Vec3 pos, float partialTick, Operation<Integer> original
    ) {
        return original.call(clientLevel, FogRendererContext.getBiomeSamplingPos(pos), partialTick);
    }
}
