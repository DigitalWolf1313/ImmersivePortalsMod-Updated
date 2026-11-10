package qouteall.imm_ptl.core.mixin.client.particle;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.render.FrontClipping;
import qouteall.imm_ptl.core.render.TransformationManager;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.teleportation.ParticleTeleportation;

@Mixin(ParticleEngine.class)
public class MixinParticleEngine implements IEParticleManager {
    @Shadow
    protected ClientLevel level;
    
    /**
     * Particle quads face the camera, so their camera basis must receive the same
     * portal and animation rotations as the world model-view transform.
     */
    @WrapMethod(method = "render")
    private void renderWithTransformedCamera(
        PoseStack matrixStack, MultiBufferSource.BufferSource bufferSource,
        LightTexture lightTexture, Camera camera, float partialTicks, Operation<Void> original
    ) {
        // Clipping is usually disabled when particles render, so enable it here.
        // It is left as it is when it is already enabled.
        boolean shouldEnableClipping = PortalRendering.isRendering() && !FrontClipping.isClippingEnabled;
        boolean oldIsRenderingParticles = RenderStates.isRenderingParticles;
        
        if (!WorldRenderInfo.isRendering() && !TransformationManager.isAnimationRunning()) {
            RenderStates.isRenderingParticles = true;
            if (shouldEnableClipping) {
                FrontClipping.setupInnerClipping(
                    PortalRendering.getActiveClippingPlane(), matrixStack.last().pose(), 0
                );
            }
            try {
                original.call(matrixStack, bufferSource, lightTexture, camera, partialTicks);
            }
            finally {
                RenderStates.isRenderingParticles = oldIsRenderingParticles;
                if (shouldEnableClipping) {
                    FrontClipping.disableClipping();
                }
            }
            return;
        }
        
        Quaternionf oldRotation = new Quaternionf(camera.rotation());
        Vector3f oldLook = new Vector3f(camera.getLookVector());
        Vector3f oldUp = new Vector3f(camera.getUpVector());
        Vector3f oldLeft = new Vector3f(camera.getLeftVector());
        
        PoseStack transformedViewStack = new PoseStack();
        transformedViewStack.mulPose(new Quaternionf(oldRotation).conjugate());
        TransformationManager.processTransformation(camera, transformedViewStack);
        Matrix4f transformedView = new Matrix4f(transformedViewStack.last().pose());
        
        try {
            RenderStates.isRenderingParticles = true;
            if (shouldEnableClipping) {
                FrontClipping.setupInnerClipping(
                    PortalRendering.getActiveClippingPlane(), matrixStack.last().pose(), 0
                );
            }
            camera.rotation().setFromUnnormalized(transformedView.invert()).normalize();
            camera.getLookVector().set(0, 0, -1).rotate(camera.rotation());
            camera.getUpVector().set(0, 1, 0).rotate(camera.rotation());
            camera.getLeftVector().set(-1, 0, 0).rotate(camera.rotation());
            original.call(matrixStack, bufferSource, lightTexture, camera, partialTicks);
        }
        finally {
            RenderStates.isRenderingParticles = oldIsRenderingParticles;
            if (shouldEnableClipping) {
                FrontClipping.disableClipping();
            }
            camera.rotation().set(oldRotation);
            camera.getLookVector().set(oldLook);
            camera.getUpVector().set(oldUp);
            camera.getLeftVector().set(oldLeft);
        }
    }
    
    // skip particle rendering for far portals
    @Inject(
        method = "Lnet/minecraft/client/particle/ParticleEngine;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onBeginRenderParticles(
        PoseStack matrixStack, MultiBufferSource.BufferSource immediate,
        LightTexture lightmapTextureManager, Camera camera, float f, CallbackInfo ci
    ) {
        if (PortalRendering.isRendering()) {
            if (PortalRendering.getPortalLayer() > 4) {
                ci.cancel();
            }
        }
    }
    
    // maybe incompatible with sodium and iris
    @Redirect(
        method = "Lnet/minecraft/client/particle/ParticleEngine;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"
        )
    )
    private void redirectBuildGeometry(Particle instance, VertexConsumer vertexConsumer, Camera camera, float v) {
        if (RenderStates.shouldRenderParticle(instance, v)) {
            instance.render(vertexConsumer, camera, v);
        }
    }
    
    // a lava ember particle can generate a smoke particle during ticking
    // avoid generating the particle into the wrong dimension
    @Inject(method = "Lnet/minecraft/client/particle/ParticleEngine;tickParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void onTickParticle(Particle particle, CallbackInfo ci) {
        if (((IEParticle) particle).portal_getWorld() != Minecraft.getInstance().level) {
            ci.cancel();
        }
    }
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void onBeginTick(CallbackInfo ci) {
        if (level != null) {
            ParticleTeleportation.refresh(level);
        }
    }
    
    @Override
    public void ip_setWorld(ClientLevel world_) {
        level = world_;
    }
    
}
