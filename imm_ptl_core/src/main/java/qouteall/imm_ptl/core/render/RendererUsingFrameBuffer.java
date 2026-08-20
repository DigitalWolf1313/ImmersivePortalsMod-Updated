package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.IPPortingLibCompat;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

import java.util.List;

public class RendererUsingFrameBuffer extends PortalRenderer {
    
    private SecondaryFrameBuffer[] secondaryFrameBuffers = new SecondaryFrameBuffer[]{
        new SecondaryFrameBuffer()
    };
    
    @Override
    public void onBeforeTranslucentRendering(PoseStack matrixStack) {
        renderPortals(matrixStack);
    }
    
    @Override
    public void onAfterTranslucentRendering(PoseStack matrixStack) {
    
    }
    
    @Override
    public void onHandRenderingEnded(PoseStack matrixStack) {
    
    }
    
    @Override
    public void finishRendering() {
    
    }
    
    @Override
    public void prepareRendering() {
        // when Portal Recursion in Compatibility mode rendering is on, keep one buffer per possible
        // recursion depth. when it's off, collapse back down to a single buffer
        // (the original behavior).
        int requiredBufferCount = IPGlobal.PortalRecursionInCompatibilityMode ?
            (PortalRendering.getMaxPortalLayer() + 1) : 1;
        
        if (secondaryFrameBuffers.length != requiredBufferCount) {
            for (SecondaryFrameBuffer fb : secondaryFrameBuffers) {
                if (fb.fb != null) {
                    fb.fb.destroyBuffers();
                }
            }
            
            secondaryFrameBuffers = new SecondaryFrameBuffer[requiredBufferCount];
            for (int i = 0; i < requiredBufferCount; i++) {
                secondaryFrameBuffers[i] = new SecondaryFrameBuffer();
            }
        }
        
        secondaryFrameBuffers[0].prepare();
        
        client.getMainRenderTarget().bindWrite(false);
        
        GlStateManager._enableDepthTest();
        
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        
        IPPortingLibCompat.setIsStencilEnabled(client.getMainRenderTarget(), false);
//        ((IEFrameBuffer) client.getMainRenderTarget()).setIsStencilBufferEnabledAndReload(false);
    }
    
    protected void doRenderPortal(
        PortalRenderable portal,
        PoseStack matrixStack
    ) {
        int portalLayer = PortalRendering.getPortalLayer();
        
        if (portalLayer > 0) {
            if (!IPGlobal.PortalRecursionInCompatibilityMode) {
                //only support one-layer portal unless the Portal Recursion in Compatibility mode toggle is enabled
                return;
            }
            
            if (portalLayer >= secondaryFrameBuffers.length) {
                // deeper than the buffers we allocated for (should track
                // PortalRendering.getMaxPortalLayer(), so this shouldn't normally trigger)
                return;
            }
        }
        
        if (!testShouldRenderPortal(portal, matrixStack)) {
            return;
        }
        
        PortalRendering.pushPortalLayer(portal.getPortalLike());
        
        RenderTarget oldFrameBuffer = client.getMainRenderTarget();
        
        SecondaryFrameBuffer secondaryFrameBuffer = secondaryFrameBuffers[portalLayer];
        secondaryFrameBuffer.prepare();
        
        ((IEMinecraftClient) client).setFrameBuffer(secondaryFrameBuffer.fb);
        secondaryFrameBuffer.fb.bindWrite(true);
        
        GlStateManager._clearColor(1, 0, 1, 1);
        GlStateManager._clearDepth(1);
        GlStateManager._clear(
            GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
            Minecraft.ON_OSX
        );
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        
        renderPortalContent(portal);
        
        ((IEMinecraftClient) client).setFrameBuffer(oldFrameBuffer);
        oldFrameBuffer.bindWrite(true);
        
        PortalRendering.popPortalLayer();
        
        CHelper.enableDepthClamp();
        renderSecondBufferIntoMainBuffer(portal, matrixStack, secondaryFrameBuffer);
        CHelper.disableDepthClamp();
        
        MyRenderHelper.debugFramebufferDepth();
    }
    
    @Override
    public void renderPortalInEntityRenderer(Portal portal) {
        //nothing
    }
    
    @Override
    public boolean replaceFrameBufferClearing() {
        return false;
    }
    
    private boolean testShouldRenderPortal(
        PortalRenderable portal,
        PoseStack matrixStack
    ) {
        FrontClipping.updateInnerClipping(matrixStack);
        return QueryManager.renderAndGetDoesAnySamplePass(() -> {
            ViewAreaRenderer.renderPortalArea(
                portal, Vec3.ZERO,
                matrixStack.last().pose(),
                RenderSystem.getProjectionMatrix(),
                true, true,
                true, true
            );
        });
    }
    
    private void renderSecondBufferIntoMainBuffer(
        PortalRenderable portal, PoseStack matrixStack, SecondaryFrameBuffer secondaryFrameBuffer
    ) {
        MyRenderHelper.drawPortalAreaWithFramebuffer(
            portal,
            secondaryFrameBuffer.fb,
            matrixStack.last().pose(),
            RenderSystem.getProjectionMatrix()
        );
    }
    
    protected void renderPortals(PoseStack matrixStack) {
        List<PortalRenderable> portalsToRender = getPortalsToRender(matrixStack);
        
        for (PortalRenderable portal : portalsToRender) {
            doRenderPortal(portal, matrixStack);
        }
    }
}
