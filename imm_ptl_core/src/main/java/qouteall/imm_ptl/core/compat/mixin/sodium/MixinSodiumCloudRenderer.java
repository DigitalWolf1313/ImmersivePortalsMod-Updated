package qouteall.imm_ptl.core.compat.mixin.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexBuffer;
import me.jellysquid.mods.sodium.client.render.immediate.CloudRenderer;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumCloudContext;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumCloudContext.CloudGeometryParameters;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

/**
 * Optimizes Sodium's cloud rendering the same way
 * {@link qouteall.imm_ptl.core.mixin.client.render.optimization.MixinLevelRenderer_Clouds}
 * optimizes vanilla's: by caching the built cloud geometry per rendered view
 * (the main view and each portal view), instead of rebuilding
 * (retessellating) it every time the rendered view switches, e.g. every time
 * rendering switches between the main view and a same-dimension portal's
 * view. {@link CloudRenderer} only keeps a single built geometry at a time,
 * so without this, every switch between views with different cloud cell
 * positions forces a full rebuild, which can take upward of 20ms.
 * <p>
 * Sodium 0.5.13 rebuilds its cloud mesh whenever
 * {@code vertexBuffer == null}, or the cloud cell position / render distance /
 * cloud render mode changed. We redirect the read of {@code vertexBuffer}
 * inside that rebuild condition (its first read, ordinal 0) to swap the
 * current geometry for a cached one belonging to the view being rendered, and
 * to archive the previous view's geometry so it is not lost when Sodium
 * rebuilds.
 */
@Mixin(value = CloudRenderer.class, remap = false)
public abstract class MixinSodiumCloudRenderer {
    
    @Shadow
    private VertexBuffer vertexBuffer;
    
    @Shadow
    private int prevCenterCellX;
    
    @Shadow
    private int prevCenterCellY;
    
    @Shadow
    private int cachedRenderDistance;
    
    @Shadow
    private CloudStatus cloudRenderMode;
    
    /**
     * {@link CloudRenderer#render}
     */
    @ModifyExpressionValue(
        method = "render",
        at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lme/jellysquid/mods/sodium/client/render/immediate/CloudRenderer;vertexBuffer:Lcom/mojang/blaze3d/vertex/VertexBuffer;",
            ordinal = 0,
            remap = true
        )
    )
    private VertexBuffer ip_onReadVertexBuffer(
        VertexBuffer original,
        @Local(type = double.class, ordinal = 0) double cameraX,
        @Local(type = double.class, ordinal = 2) double cameraZ,
        @Local(type = float.class, ordinal = 0) float ticks,
        @Local(type = float.class, ordinal = 1) float tickDelta
    ) {
        if (RenderStates.getRenderedPortalNum() == 0 || !IPGlobal.cloudOptimization) {
            return original;
        }
        
        // mirror the parameters Sodium itself computes to decide the rebuild
        double cloudTime = (ticks + tickDelta) * 0.03F;
        double cloudCenterX = cameraX + cloudTime;
        double cloudCenterZ = cameraZ + 0.33D;
        int renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();
        int centerCellX = (int) Math.floor(cloudCenterX / 12);
        int centerCellZ = (int) Math.floor(cloudCenterZ / 12);
        CloudStatus currentStatus = Minecraft.getInstance().options.getCloudsType();
        CloudGeometryParameters parameters =
            new CloudGeometryParameters(centerCellX, centerCellZ, renderDistance, currentStatus);
        
        VertexBuffer current = original;
        
        // the currently-built geometry already matches this view -- nothing to swap
        if (current != null
            && this.prevCenterCellX == centerCellX
            && this.prevCenterCellY == centerCellZ
            && this.cachedRenderDistance == renderDistance
            && this.cloudRenderMode == currentStatus) {
            return current;
        }
        
        // the currently-built geometry (if any) belongs to a different view
        // (different cell position/render distance/mode). Archive it instead
        // of letting Sodium overwrite or discard it, so it can be reused
        // without rebuilding next time that view is rendered
        if (current != null) {
            SodiumCloudContext.appendContext(new SodiumCloudContext(
                current,
                new CloudGeometryParameters(
                    this.prevCenterCellX, this.prevCenterCellY, this.cachedRenderDistance, this.cloudRenderMode
                )
            ));
        }
        
        SodiumCloudContext cached = SodiumCloudContext.findAndTakeGeometry(parameters);
        
        if (cached != null) {
            this.vertexBuffer = cached.vertexBuffer;
            // make Sodium's rebuild condition evaluate to false so it reuses
            // the cached geometry instead of rebuilding
            this.prevCenterCellX = centerCellX;
            this.prevCenterCellY = centerCellZ;
            this.cachedRenderDistance = renderDistance;
            this.cloudRenderMode = currentStatus;
            return this.vertexBuffer;
        }
        
        // no cached geometry available for this view. Return null (instead of
        // the archived geometry above) so that Sodium allocates a fresh
        // VertexBuffer rather than reusing (and corrupting) the one we just
        // archived
        this.vertexBuffer = null;
        return null;
    }
}
