package qouteall.imm_ptl.core.render.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.portal.Portal;

public class RendererDummy extends PortalRenderer {
    @Override
    public void onBeforeTranslucentRendering(org.joml.Matrix4f modelView) {}
    @Override
    public boolean replaceFrameBufferClearing() {
        return false;
    }
    
    @Override
    public void prepareRendering() {
    
    }
    
    @Override
    public void onHandRenderingEnded() {
    
    }
    
    @Override
    public void finishRendering() {
    
    }
    
    protected void doRenderPortal(
        Portal portal,
        PoseStack matrixStack
    ) {
    
    }
    
    @Override
    public void renderPortalInEntityRenderer(Portal portal) {
    
    }
}
