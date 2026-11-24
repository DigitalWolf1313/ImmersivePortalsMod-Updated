package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class OverlayRendering {
    private static final RandomSource random = RandomSource.create();
    
    /**
     * The pose is captured during the entity pass and the overlay is drawn later by {@link #flushPendingOverlays()}.
     * The layer is the portal layer of the world render that the entity pass belongs to.
     */
    private record PendingOverlay(BreakablePortalEntity portal, Matrix4f pose, Matrix3f normal, int layer) {}
    
    private static final List<PendingOverlay> pending = new ArrayList<>();
    
    @Nullable
    private static ByteBufferBuilder byteBuffer;
    
    public static boolean shouldRenderOverlay(Portal portal) {
        if (portal instanceof BreakablePortalEntity breakablePortalEntity) {
            if (breakablePortalEntity.getActualOverlay() != null) {
                return breakablePortalEntity.isInFrontOfPortal(CHelper.getCurrentCameraPos());
            }
        }
        return false;
    }
    
    private static boolean shaderOverlayWarned = false;
    
    public static void onRenderPortalEntity(
        Portal portal,
        PoseStack matrixStack,
        MultiBufferSource vertexConsumerProvider
    ) {
        if (IrisInterface.invoker.isShaders()) {
            if (!shaderOverlayWarned) {
                shaderOverlayWarned = true;
                CHelper.printChat("[Immersive Portals] Portal overlay cannot be rendered with shaders");
            }
            
            return;
        }
        
        if (portal instanceof BreakablePortalEntity breakablePortal) {
            PoseStack.Pose last = matrixStack.last();
            pending.add(new PendingOverlay(
                breakablePortal,
                new Matrix4f(last.pose()),
                new Matrix3f(last.normal()),
                PortalRendering.getPortalLayer()
            ));
        }
    }
    
    public static List<BakedQuad> getQuads(BakedModel model, BlockState blockState, Vec3 portalNormal) {
        Direction facing = Direction.getApproximateNearest(portalNormal.x, portalNormal.y, portalNormal.z);
        
        List<BakedQuad> result = new ArrayList<>();
        
        result.addAll(model.getQuads(blockState, facing, random));
        
        result.addAll(model.getQuads(blockState, null, random));
        
        if (result.isEmpty()) {
            for (Direction direction : Direction.values()) {
                result.addAll(model.getQuads(blockState, direction, random));
            }
        }
        
        return result;
    }
    
    /**
     * Draws the overlays queued by the entity pass of the current world render.
     * It must be called after the portal views are rendered, because the portal view area restores depth at the portal plane.
     * The overlay does not write depth, so the translucent terrain behind it is still drawn.
     * The polygon offset keeps the overlay from z-fighting with the portal plane.
     */
    public static void flushPendingOverlays() {
        if (pending.isEmpty()) {
            return;
        }
        
        int layer = PortalRendering.getPortalLayer();
        
        List<PendingOverlay> toRender = new ArrayList<>();
        for (PendingOverlay overlay : pending) {
            if (overlay.layer() == layer) {
                toRender.add(overlay);
            }
        }
        
        // Entries of other layers are stale when the outermost world render reaches here.
        if (layer == 0) {
            pending.clear();
        }
        else {
            pending.removeAll(toRender);
        }
        
        if (toRender.isEmpty()) {
            return;
        }
        
        if (byteBuffer == null) {
            byteBuffer = new ByteBufferBuilder(262144);
        }
        
        BufferBuilder buffer = new BufferBuilder(
            byteBuffer, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY
        );
        
        for (PendingOverlay overlay : toRender) {
            buildOverlay(overlay, buffer);
        }
        
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        
        RenderType renderType = Sheets.translucentItemSheet();
        renderType.setupRenderState();
        try {
            RenderSystem.depthMask(false);
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-1.0F, -10.0F);
            BufferUploader.drawWithShader(mesh);
        }
        finally {
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.depthMask(true);
            renderType.clearRenderState();
        }
    }
    
    /**
     * {@link net.minecraft.client.renderer.entity.FallingBlockRenderer}
     */
    private static void buildOverlay(PendingOverlay pendingOverlay, BufferBuilder buffer) {
        BreakablePortalEntity portal = pendingOverlay.portal();
        
        BreakablePortalEntity.OverlayInfo overlay = portal.getActualOverlay();
        
        if (overlay == null) {
            return;
        }
        
        BlockState blockState = overlay.blockState();
        
        if (blockState == null) {
            return;
        }
        
        BlockPortalShape blockPortalShape = portal.blockPortalShape;
        if (blockPortalShape == null) {
            return;
        }
        
        BlockRenderDispatcher blockRenderManager = Minecraft.getInstance().getBlockRenderer();
        
        PoseStack matrixStack = new PoseStack();
        matrixStack.last().pose().set(pendingOverlay.pose());
        matrixStack.last().normal().set(pendingOverlay.normal());
        
        Vec3 offset = portal.getNormal().scale(overlay.offset());
        
        Vec3 pos = portal.position();
        
        matrixStack.translate(offset.x, offset.y, offset.z);
        
        BakedModel model = blockRenderManager.getBlockModel(blockState);
        
        List<BakedQuad> quads = getQuads(model, blockState, portal.getNormal());
        
        random.setSeed(0);
        
        for (BlockPos blockPos : blockPortalShape.area) {
            matrixStack.pushPose();
            matrixStack.translate(
                blockPos.getX() - pos.x, blockPos.getY() - pos.y, blockPos.getZ() - pos.z
            );
            
            if (overlay.rotation() != null) {
                matrixStack.mulPose(overlay.rotation().toMcQuaternion());
            }
            
            for (BakedQuad quad : quads) {
                SodiumInterface.invoker.markSpriteActive(quad.getSprite());
                buffer.putBulkData(
                    matrixStack.last(),
                    quad,
                    new float[]{1.0F, 1.0F, 1.0F, 1.0F},
                    1.0f, 1.0f, 1.0f, (float) overlay.opacity(),
                    new int[]{14680304, 14680304, 14680304, 14680304},//packed light value
                    OverlayTexture.NO_OVERLAY,
                    true
                );
            }
            
            matrixStack.popPose();
        }
    }
}
