package qouteall.imm_ptl.core.mixin.client.particle;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.render.TransformationManager;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@SuppressWarnings("resource")
@Mixin(ParticleEngine.class)
public class MixinParticleEngine implements IEParticleManager {
    @Shadow
    protected ClientLevel level;
    
    /**
     * Particles use the camera orientation to build camera-facing quads, while portal
     * rendering applies its rotation to the world model-view matrix. Give them the
     * corresponding transformed camera orientation for the duration of this render.
     */
    @WrapMethod(method = "render")
    private void renderWithTransformedCamera(
        LightTexture lightTexture, Camera camera, float partialTick, Operation<Void> original
    ) {
        if (!WorldRenderInfo.isRendering() && !TransformationManager.isAnimationRunning()) {
            original.call(lightTexture, camera, partialTick);
            return;
        }
        
        Quaternionf oldRotation = new Quaternionf(camera.rotation());
        Vector3f oldLook = new Vector3f(camera.getLookVector());
        Vector3f oldUp = new Vector3f(camera.getUpVector());
        Vector3f oldLeft = new Vector3f(camera.getLeftVector());
        
        Matrix4f transformedView = TransformationManager.processTransformation(
            camera, new Matrix4f().rotation(new Quaternionf(oldRotation).conjugate())
        );
        
        try {
            // A particle's camera orientation is the inverse of the view rotation.
            camera.rotation().setFromUnnormalized(transformedView.invert()).normalize();
            
            // Sodium uses these vectors directly instead of camera.rotation().
            camera.getLookVector().set(0, 0, -1).rotate(camera.rotation());
            camera.getUpVector().set(0, 1, 0).rotate(camera.rotation());
            camera.getLeftVector().set(-1, 0, 0).rotate(camera.rotation());
            original.call(lightTexture, camera, partialTick);
        }
        finally {
            camera.rotation().set(oldRotation);
            camera.getLookVector().set(oldLook);
            camera.getUpVector().set(oldUp);
            camera.getLeftVector().set(oldLeft);
        }
    }
    
    // skip particle rendering for far portals
    @Inject(
        method = "render",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onBeginRenderParticles(
        LightTexture lightTexture, Camera camera, float f, CallbackInfo ci
    ) {
        if (PortalRendering.isRendering()) {
            if (RenderStates.getRenderedPortalNum() > 4) {
                ci.cancel();
            }
        }
    }
    
    // maybe incompatible with sodium and iris
    @WrapWithCondition(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"
        )
    )
    private boolean redirectBuildGeometry(
        Particle instance, VertexConsumer vertexConsumer, Camera camera, float v
    ) {
        return RenderStates.shouldRenderParticle(instance);
    }
    
    // a lava ember particle can generate a smoke particle during ticking
    // avoid generating the particle into the wrong dimension
    @Inject(method = "Lnet/minecraft/client/particle/ParticleEngine;tickParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void onTickParticle(Particle particle, CallbackInfo ci) {
        if (((IEParticle) particle).portal_getWorld() != Minecraft.getInstance().level) {
            ci.cancel();
        }
    }
    
    @Override
    public void ip_setWorld(ClientLevel world_) {
        level = world_;
    }
    
}
