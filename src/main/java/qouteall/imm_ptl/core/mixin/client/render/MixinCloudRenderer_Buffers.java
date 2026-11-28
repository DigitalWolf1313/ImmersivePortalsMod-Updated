package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.IPCloudBuffers;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@Mixin(CloudRenderer.class)
public class MixinCloudRenderer_Buffers {
    @Shadow
    @Final
    private MappableRingBuffer ubo;
    
    // A portal view uses its own cloud uniform buffer, see IPCloudBuffers.
    @Inject(method = "render", at = @At("HEAD"))
    private void ip_onRenderHead(CallbackInfo ci) {
        if (WorldRenderInfo.isRendering()) {
            IPCloudBuffers.acquire();
        }
        else {
            IPCloudBuffers.releaseCurrent();
        }
    }
    
    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MappableRingBuffer;currentBuffer()Lcom/mojang/blaze3d/buffers/GpuBuffer;"
        )
    )
    private GpuBuffer ip_useOwnUboForPortalView(MappableRingBuffer instance, Operation<GpuBuffer> original) {
        if (instance == this.ubo && IPCloudBuffers.getCurrent() != null) {
            return IPCloudBuffers.getCurrent();
        }
        return original.call(instance);
    }
    
    @Inject(method = "endFrame", at = @At("HEAD"))
    private void ip_onEndFrame(CallbackInfo ci) {
        IPCloudBuffers.onEndFrame();
    }
}
