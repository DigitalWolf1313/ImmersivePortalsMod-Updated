package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Vanilla's circular entity shadow, sampled from the destination world instead of the entity's own world.
 * The geometry mirrors the vanilla shadow code, so the shadow render type keeps working with shader packs.
 */
@Environment(EnvType.CLIENT)
public class ProjectedEntityShadow {
    private static final RenderType SHADOW_RENDER_TYPE = RenderType.entityShadow(
        ResourceLocation.withDefaultNamespace("textures/misc/shadow.png")
    );
    
    /**
     * Draws the shadow centered on destPos.
     * The size is the unscaled vanilla radius, and the portal scaling is applied here.
     */
    public static void render(
        Matrix4f baseModelView, Vec3 cameraPos, MultiBufferSource buffer, ClientLevel level,
        Vec3 destPos, float weight, float size, float scaling
    ) {
        float radius = Math.min(size * scaling, 32.0F);
        float reach = Math.min(weight / 0.5F, size) * scaling;
        double x = destPos.x;
        double y = destPos.y;
        double z = destPos.z;
        
        int minX = Mth.floor(x - radius);
        int maxX = Mth.floor(x + radius);
        int minY = Mth.floor(y - reach);
        int maxY = Mth.floor(y);
        int minZ = Mth.floor(z - radius);
        int maxZ = Mth.floor(z + radius);
        
        Matrix4f pose = new Matrix4f(baseModelView).translate(
            (float) (x - cameraPos.x), (float) (y - cameraPos.y), (float) (z - cameraPos.z)
        );
        VertexConsumer consumer = buffer.getBuffer(SHADOW_RENDER_TYPE);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        
        for (int bz = minZ; bz <= maxZ; bz++) {
            for (int bx = minX; bx <= maxX; bx++) {
                pos.set(bx, 0, bz);
                ChunkAccess chunk = level.getChunk(pos);
                
                for (int by = minY; by <= maxY; by++) {
                    pos.setY(by);
                    // The darkening is measured in the entity's own scale.
                    float blockWeight = weight - (float) ((y - by) / scaling) * 0.5F;
                    renderBlockShadow(pose, consumer, chunk, level, pos, x, y, z, radius, blockWeight);
                }
            }
        }
    }
    
    private static void renderBlockShadow(
        Matrix4f pose, VertexConsumer consumer, ChunkAccess chunk, ClientLevel level, BlockPos pos,
        double x, double y, double z, float radius, float weight
    ) {
        BlockPos below = pos.below();
        BlockState state = chunk.getBlockState(below);
        if (state.getRenderShape() == RenderShape.INVISIBLE || level.getMaxLocalRawBrightness(pos) <= 3) {
            return;
        }
        if (!state.isCollisionShapeFullBlock(chunk, below)) {
            return;
        }
        
        VoxelShape shape = state.getShape(chunk, below);
        if (shape.isEmpty()) {
            return;
        }
        
        float brightness = LightTexture.getBrightness(level.dimensionType(), level.getMaxLocalRawBrightness(pos));
        float alpha = weight * 0.5F * brightness;
        if (alpha < 0.0F) {
            return;
        }
        alpha = Math.min(alpha, 1.0F);
        
        int color = FastColor.ARGB32.color(Mth.floor(alpha * 255.0F), 255, 255, 255);
        AABB bounds = shape.bounds();
        float x0 = (float) (pos.getX() + bounds.minX - x);
        float x1 = (float) (pos.getX() + bounds.maxX - x);
        float yy = (float) (pos.getY() + bounds.minY - y);
        float z0 = (float) (pos.getZ() + bounds.minZ - z);
        float z1 = (float) (pos.getZ() + bounds.maxZ - z);
        float u0 = -x0 / 2.0F / radius + 0.5F;
        float u1 = -x1 / 2.0F / radius + 0.5F;
        float v0 = -z0 / 2.0F / radius + 0.5F;
        float v1 = -z1 / 2.0F / radius + 0.5F;
        
        vertex(pose, consumer, color, x0, yy, z0, u0, v0);
        vertex(pose, consumer, color, x0, yy, z1, u0, v1);
        vertex(pose, consumer, color, x1, yy, z1, u1, v1);
        vertex(pose, consumer, color, x1, yy, z0, u1, v0);
    }
    
    private static void vertex(
        Matrix4f pose, VertexConsumer consumer, int color,
        float x, float y, float z, float u, float v
    ) {
        Vector3f p = pose.transformPosition(x, y, z, new Vector3f());
        consumer.addVertex(p.x(), p.y(), p.z(), color, u, v, OverlayTexture.NO_OVERLAY, 15728880, 0.0F, 1.0F, 0.0F);
    }
}
