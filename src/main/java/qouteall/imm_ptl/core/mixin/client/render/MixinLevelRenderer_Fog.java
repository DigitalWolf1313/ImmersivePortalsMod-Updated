package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.IPFogBuffers;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.PortalFogOverlay;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

import java.nio.ByteBuffer;

@Mixin(FogRenderer.class)
public class MixinLevelRenderer_Fog {
    @Shadow
    private static boolean fogEnabled;
    
    @WrapOperation(
        method = "setupFog",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"
        )
    )
    private void ip_transformPortalFogDistance(
        FogRenderer instance, ByteBuffer buffer, int position, Vector4f fogColor,
        float environmentalStart, float environmentalEnd,
        float renderDistanceStart, float renderDistanceEnd,
        float skyEnd, float cloudEnd, Operation<Void> original
    ) {
        float newEnvironmentalStart = MyRenderHelper.transformFogDistance(environmentalStart);
        float newEnvironmentalEnd = MyRenderHelper.transformFogDistance(environmentalEnd);
        float newRenderDistanceStart = MyRenderHelper.transformFogDistance(renderDistanceStart);
        float newRenderDistanceEnd = MyRenderHelper.transformFogDistance(renderDistanceEnd);
        
        PortalFogOverlay.recordWorldFog(
            fogEnabled, fogColor,
            newEnvironmentalStart, newEnvironmentalEnd,
            newRenderDistanceStart, newRenderDistanceEnd,
            skyEnd, cloudEnd
        );
        
        original.call(
            instance, buffer, position, fogColor,
            newEnvironmentalStart, newEnvironmentalEnd,
            newRenderDistanceStart, newRenderDistanceEnd,
            skyEnd, cloudEnd
        );
    }
    
    // A portal view writes its fog into its own buffer, see IPFogBuffers.
    @WrapOperation(
        method = "setupFog",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MappableRingBuffer;currentBuffer()Lcom/mojang/blaze3d/buffers/GpuBuffer;"
        )
    )
    private GpuBuffer ip_useOwnBufferForPortalViewSetup(MappableRingBuffer instance, Operation<GpuBuffer> original) {
        if (WorldRenderInfo.isRendering()) {
            return IPFogBuffers.acquireForSetup();
        }
        return original.call(instance);
    }
    
    @WrapOperation(
        method = "getBuffer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/MappableRingBuffer;currentBuffer()Lcom/mojang/blaze3d/buffers/GpuBuffer;"
        )
    )
    private GpuBuffer ip_useOwnBufferForPortalView(MappableRingBuffer instance, Operation<GpuBuffer> original) {
        if (WorldRenderInfo.isRendering() && IPFogBuffers.getCurrent() != null) {
            return IPFogBuffers.getCurrent();
        }
        return original.call(instance);
    }
    
    @Inject(method = "endFrame", at = @At("HEAD"))
    private void ip_onEndFrame(CallbackInfo ci) {
        IPFogBuffers.onEndFrame();
    }
}
