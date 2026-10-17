package qouteall.imm_ptl.core.compat.mixin.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.compat.iris_compatibility.ExperimentalIrisPortalRenderer;
import qouteall.imm_ptl.core.compat.iris_compatibility.IEIrisNewWorldRenderingPipeline;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisRenderTargetSharing;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

import java.util.Map;

@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class MixinIrisRenderingPipeline implements IEIrisNewWorldRenderingPipeline {
    @Shadow private boolean isRenderingWorld;
    
    @Shadow private ShadowRenderTargets shadowRenderTargets;
    @Shadow @Final private CompositeRenderer compositeRenderer;
    @Shadow @Final private FrameUpdateNotifier updateNotifier;
    @Shadow @Final private CustomUniforms customUniforms;
    
    @Unique private int ip_seenResizeGeneration;
    
    // let cross-dimension portals share the G-buffers of the outer world (see IrisRenderTargetSharing)
    @WrapOperation(
        method = "<init>",
        at = @At(
            value = "NEW",
            target = "(IIIILnet/irisshaders/iris/gl/texture/DepthBufferFormat;Ljava/util/Map;Lnet/irisshaders/iris/shaderpack/properties/PackDirectives;)Lnet/irisshaders/iris/targets/RenderTargets;"
        )
    )
    private RenderTargets ip_shareRenderTargets(
        int width, int height, int depthTexture, int depthBufferVersion,
        DepthBufferFormat depthFormat,
        Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> renderTargetSettings,
        PackDirectives packDirectives,
        Operation<RenderTargets> original
    ) {
        if (IrisRenderTargetSharing.shouldShare()) {
            RenderTargets reusable = IrisRenderTargetSharing.getReusableRenderTargets();
            if (reusable != null) {
                ip_seenResizeGeneration = IrisRenderTargetSharing.getResizeGeneration();
                return reusable;
            }
        }
        
        RenderTargets created = original.call(
            width, height, depthTexture, depthBufferVersion, depthFormat,
            renderTargetSettings, packDirectives
        );
        
        if (IrisRenderTargetSharing.shouldShare()) {
            IrisRenderTargetSharing.registerRenderTargets(created);
        }
        
        ip_seenResizeGeneration = IrisRenderTargetSharing.getResizeGeneration();
        return created;
    }
    
    // Iris reports a resize only to the first pipeline using the shared targets, so report it to the others too
    @WrapOperation(
        method = "beginLevelRendering",
        at = @At(
            value = "INVOKE",
            target = "Lnet/irisshaders/iris/targets/RenderTargets;resizeIfNeeded(IIIILnet/irisshaders/iris/gl/texture/DepthBufferFormat;Lnet/irisshaders/iris/shaderpack/properties/PackDirectives;)Z"
        )
    )
    private boolean ip_reportSharedResize(
        RenderTargets renderTargets,
        int depthBufferVersion, int depthTextureId, int width, int height,
        DepthBufferFormat depthFormat, PackDirectives packDirectives,
        Operation<Boolean> original
    ) {
        boolean changed = original.call(
            renderTargets, depthBufferVersion, depthTextureId, width, height, depthFormat, packDirectives
        );
        
        if (!IrisRenderTargetSharing.isShared(renderTargets)) {
            return changed;
        }
        
        if (changed) {
            IrisRenderTargetSharing.onResized();
        }
        
        int generation = IrisRenderTargetSharing.getResizeGeneration();
        boolean needsUpdate = changed || ip_seenResizeGeneration != generation;
        ip_seenResizeGeneration = generation;
        return needsUpdate;
    }
    
    @WrapOperation(
        method = "destroy",
        at = @At(
            value = "INVOKE",
            target = "Lnet/irisshaders/iris/targets/RenderTargets;destroy()V"
        )
    )
    private void ip_destroySharedRenderTargets(RenderTargets renderTargets, Operation<Void> original) {
        if (IrisRenderTargetSharing.shouldDestroy(renderTargets)) {
            original.call(renderTargets);
        }
    }
    
    @Inject(
        method = "finalizeLevelRendering", at = @At("HEAD"), cancellable = true
    )
    private void onFinalizeLevelRendering(CallbackInfo ci) {
        if (IPCGlobal.renderer instanceof ExperimentalIrisPortalRenderer) {
            if (PortalRendering.isRendering()) {
                ci.cancel();
            }
        }
    }
    
    @Inject(
        method = "beginTranslucents",
        at = @At(
            value = "INVOKE",
            target = "Lnet/irisshaders/iris/pipeline/CompositeRenderer;renderAll()V",
            shift = At.Shift.AFTER
        )
    )
    private void onAfterDeferredCompositeRendering(CallbackInfo ci) {
        if (IPCGlobal.renderer instanceof ExperimentalIrisPortalRenderer r) {
            r.onAfterIrisDeferredCompositeRendering();
        }
    }
    
    @Override
    public void ip_setIsRenderingWorld(boolean cond) {
        isRenderingWorld = cond;
    }
    
    @Override
    public ShadowRenderTargets ip_getShadowRenderTargets() {
        return shadowRenderTargets;
    }
    
    @Override
    public void ip_refreshFrameState() {
        updateNotifier.onNewFrame();
        customUniforms.update();
    }
}
