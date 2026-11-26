package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.ResourceLocation;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.mc_utils.WireRenderingHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@Environment(EnvType.CLIENT)
public class PortalEntityRenderer extends EntityRenderer<Portal, PortalEntityRenderer.PortalRenderState> {
    public static class PortalRenderState extends EntityRenderState {
        public Portal portal;
    }
    
    public PortalEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
    
    @Override
    public void render(PortalRenderState state, PoseStack matrixStack, MultiBufferSource bufferSource, int light) {
        Portal portal = state.portal;
        IPCGlobal.renderer.renderPortalInEntityRenderer(portal);
        
        if (OverlayRendering.shouldRenderOverlay(portal)) {
            OverlayRendering.onRenderPortalEntity(portal, matrixStack, bufferSource);
        }
    
        if (IPGlobal.debugRenderPortalShapeMesh && !PortalRendering.isRendering()) {
            VertexConsumer lineVertexConsumer = bufferSource.getBuffer(RenderType.lines());
            WireRenderingHelper.renderPortalShapeMeshDebug(
                matrixStack, lineVertexConsumer, portal
            );
        }
        
        super.render(state, matrixStack, bufferSource, light);
    }
    
    @Override
    public PortalRenderState createRenderState() {
        return new PortalRenderState();
    }

    @Override
    public void extractRenderState(Portal portal, PortalRenderState state, float partialTick) {
        super.extractRenderState(portal, state, partialTick);
        state.portal = portal;
    }
    
    
}
