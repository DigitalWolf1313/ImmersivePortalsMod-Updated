package qouteall.imm_ptl.core.compat.iris_compatibility;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.IPPortingLibCompat;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalRenderInfo;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.SecondaryFrameBuffer;
import qouteall.imm_ptl.core.render.ViewAreaRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;

import java.util.List;

import static org.lwjgl.opengl.GL11.GL_STENCIL_TEST;

public class IrisCompatibilityPortalRenderer extends PortalRenderer {
    
    public static final IrisCompatibilityPortalRenderer instance = new IrisCompatibilityPortalRenderer(false);
    public static final IrisCompatibilityPortalRenderer debugModeInstance =
        new IrisCompatibilityPortalRenderer(true);
    
    // one buffer per possible recursion depth when Portal Recursion in Compatibility mode rendering is
    // enabled (index == PortalRendering.getPortalLayer()). collapses down to a single
    // buffer (index 0 only) when the "PortalRecursionInCompatibilityMode" setting is off,
    // which is the original single-layer behavior.
    private SecondaryFrameBuffer[] deferredBuffers = new SecondaryFrameBuffer[]{
        new SecondaryFrameBuffer()
    };
    
    // TODO figure out why this field existed in old versions
    // per-layer version of the above, indexed the same way
    private Matrix4f[] passingModelViews = new Matrix4f[]{new Matrix4f()};
    
    public boolean isDebugMode;
    
    public IrisCompatibilityPortalRenderer(boolean isDebugMode) {
        this.isDebugMode = isDebugMode;
    }
    
    @Override
    public boolean replaceFrameBufferClearing() {
        client.getMainRenderTarget().bindWrite(false);
        
        return false;
    }
    
    @Override
    public void onBeforeTranslucentRendering(Matrix4f modelView) {
        int portalLayer = PortalRendering.getPortalLayer();
        
        if (portalLayer > 0 && !IPGlobal.PortalRecursionInCompatibilityMode) {
            // this renderer only supports one-layer portal unless Portal Recursion in Compatibility mode is enabled
            return;
        }
        
        if (portalLayer < passingModelViews.length) {
            passingModelViews[portalLayer] = modelView;
        }
        
        GL11.glDisable(GL_STENCIL_TEST);
    }
    
    @Override
    public void onAfterTranslucentRendering(Matrix4f modelView) {
    
    }
    
    @Override
    public void finishRendering() {
        GL11.glDisable(GL_STENCIL_TEST);
    }
    
    @Override
    public void prepareRendering() {
        // when portal-in-portal rendering is on, keep one buffer per possible
        // recursion depth. when it's off, collapse back down to a single buffer
        // (the original behavior).
        int requiredBufferCount = IPGlobal.PortalRecursionInCompatibilityMode ?
            (PortalRendering.getMaxPortalLayer() + 1) : 1;
        
        if (deferredBuffers.length != requiredBufferCount) {
            for (SecondaryFrameBuffer fb : deferredBuffers) {
                if (fb.fb != null) {
                    fb.fb.destroyBuffers();
                }
            }
            
            deferredBuffers = new SecondaryFrameBuffer[requiredBufferCount];
            for (int i = 0; i < requiredBufferCount; i++) {
                deferredBuffers[i] = new SecondaryFrameBuffer();
            }
            
            passingModelViews = new Matrix4f[requiredBufferCount];
            for (int i = 0; i < requiredBufferCount; i++) {
                passingModelViews[i] = new Matrix4f();
            }
        }
        
        SecondaryFrameBuffer deferredBuffer = deferredBuffers[0];
        
        deferredBuffer.prepare();
        
        deferredBuffer.fb.setClearColor(1, 0, 0, 0);
        deferredBuffer.fb.clear(Minecraft.ON_OSX);
        
        IPPortingLibCompat.setIsStencilEnabled(
            client.getMainRenderTarget(), false
        );
        
        // Iris now use vanilla framebuffer's depth
        client.getMainRenderTarget().bindWrite(false);
    }
    
    protected void doRenderPortal(Portal portal, Matrix4f modelView) {
        int portalLayer = PortalRendering.getPortalLayer();
        
        if (portalLayer > 0) {
            if (!IPGlobal.PortalRecursionInCompatibilityMode) {
                // this renderer only supports one-layer portal unless Portal Recursion in Compatibility mode is enabled
                return;
            }
            
            if (portalLayer >= deferredBuffers.length) {
                // deeper than the buffers we allocated for (should track
                // PortalRendering.getMaxPortalLayer(), so this shouldn't normally trigger)
                return;
            }
        }
        
        if (!testShouldRenderPortal(portal, modelView)) {
            return;
        }
        
        client.getMainRenderTarget().bindWrite(true);
        
        PortalRendering.pushPortalLayer(portal);
        
        renderPortalContent(portal);
        
        PortalRendering.popPortalLayer();
        
        CHelper.enableDepthClamp();
        
        SecondaryFrameBuffer deferredBuffer = deferredBuffers[portalLayer];
        
        if (!isDebugMode) {
            // draw portal content to the deferred buffer
            deferredBuffer.fb.bindWrite(true);
            MyRenderHelper.drawPortalAreaWithFramebuffer(
                portal,
                client.getMainRenderTarget(),
                modelView,
                RenderSystem.getProjectionMatrix()
            );
        }
        else {
            deferredBuffer.fb.bindWrite(true);
            MyRenderHelper.drawScreenFrameBuffer(
                client.getMainRenderTarget(),
                true, true
            );
        }
        
        CHelper.disableDepthClamp();
        
        RenderSystem.colorMask(true, true, true, true);
        
        client.getMainRenderTarget().bindWrite(true);
    }

    
    @Override
    public void invokeWorldRendering(
        WorldRenderInfo worldRenderInfo
    ) {
        
        IrisInterface.invoker.updatePerFrameUniforms();
        
        MyGameRenderer.renderWorldNew(
            worldRenderInfo,
            Runnable::run
        );
    }
    
    @Override
    public void renderPortalInEntityRenderer(Portal portal) {
    
    }
    
    private boolean testShouldRenderPortal(Portal portal, Matrix4f modelView) {
        
        //reset projection matrix
//        client.gameRenderer.loadProjectionMatrix(RenderStates.basicProjectionMatrix);
        
        int portalLayer = PortalRendering.getPortalLayer();
        SecondaryFrameBuffer deferredBuffer = deferredBuffers[portalLayer];
        
        deferredBuffer.fb.bindWrite(true);
        
        return PortalRenderInfo.renderAndDecideVisibility(portal, () -> {
            
            ViewAreaRenderer.renderPortalArea(
                portal, Vec3.ZERO,
                modelView,
                RenderSystem.getProjectionMatrix(),
                true, false, false, true
            );
        });
    }
    
    @Override
    public void onBeforeHandRendering(Matrix4f modelView) {
        int portalLayer = PortalRendering.getPortalLayer();
        
        if (portalLayer > 0) {
            if (!IPGlobal.PortalRecursionInCompatibilityMode) {
                // this renderer only supports one-layer portal unless Portal Recursion in Compatibility mode is enabled
                return;
            }
            
            if (portalLayer >= deferredBuffers.length) {
                // deeper than the buffers we allocated for (should track
                // PortalRendering.getMaxPortalLayer(), so this shouldn't normally trigger)
                return;
            }
        }
        
        CHelper.checkGlError();
        
        SecondaryFrameBuffer deferredBuffer = deferredBuffers[portalLayer];
        // buffers for recursion depth > 0 are not sized/allocated in prepareRendering(),
        // so make sure this one is ready before we copy into it
        deferredBuffer.prepare();
        
        // save the main framebuffer (this recursion depth's freshly rendered world) to
        // its deferred buffer
        IPIrisHelper.newCopyDepthStencil(
            client.getMainRenderTarget(),
            deferredBuffer.fb
        );
        IPIrisHelper.copyColor(
            client.getMainRenderTarget(),
            deferredBuffer.fb
        );
//        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, client.getMainRenderTarget().frameBufferId);
//        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, deferredBuffer.fb.frameBufferId);
//        GL30.glBlitFramebuffer(
//            0, 0, deferredBuffer.fb.width, deferredBuffer.fb.height,
//            0, 0, deferredBuffer.fb.width, deferredBuffer.fb.height,
//            GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
//            GL_NEAREST
//        );
        
        CHelper.checkGlError();
        
        Matrix4f effectiveModelView = portalLayer < passingModelViews.length ?
            passingModelViews[portalLayer] : modelView;
        
        // recursing here (via doRenderPortal -> renderPortalContent) is what lets
        // portals seen through other portals be rendered when Portal Recursion in Compatibility mode is enabled
        renderPortals(effectiveModelView);
        
        RenderTarget mainFrameBuffer = client.getMainRenderTarget();
        mainFrameBuffer.bindWrite(true);
        
        MyRenderHelper.drawScreenFrameBuffer(
            deferredBuffer.fb,
            false,
            false
        );
    }
    
    @Override
    public void onHandRenderingEnded() {
    
    }
    
    protected void renderPortals(Matrix4f modelView) {
        List<Portal> portalsToRender = getPortalsToRender(modelView);
        
        for (Portal portal : portalsToRender) {
            doRenderPortal(portal, modelView);
        }
    }
}
