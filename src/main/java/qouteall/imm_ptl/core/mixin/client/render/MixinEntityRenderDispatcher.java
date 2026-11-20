package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;


@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {
    @Inject(
        method = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onShouldRenderEntity(
        Entity entity_1,
        Frustum frustum_1,
        double double_1,
        double double_2,
        double double_3,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (!CrossPortalEntityRenderer.shouldRenderEntityNow(entity_1)) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
    
    // The shadow strength fades with distance, so a projection must measure it from its destination position.
    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;distanceToSqr(DDD)D"
        )
    )
    private double ip_projectionShadowDistance(
        EntityRenderDispatcher instance, double x, double y, double z, Operation<Double> original
    ) {
        if (CrossPortalEntityRenderer.isRenderingEntityProjection) {
            Vec3 pos = CrossPortalEntityRenderer.transformForProjection(new Vec3(x, y, z));
            return original.call(instance, pos.x, pos.y, pos.z);
        }
        return original.call(instance, x, y, z);
    }
    
    // The vanilla shadow would sample the destination world at the entity's source coordinates.
    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;FFLnet/minecraft/world/level/LevelReader;F)V"
        )
    )
    private static void ip_renderProjectionShadow(
        PoseStack poseStack, MultiBufferSource buffer, Entity entity, float weight, float partialTick,
        LevelReader level, float size, Operation<Void> original
    ) {
        if (CrossPortalEntityRenderer.isRenderingEntityProjection) {
            // The Iris shadow map has no circle shadows.
            if (!IrisInterface.invoker.isRenderingShadowMap()) {
                CrossPortalEntityRenderer.renderProjectionShadow(buffer, weight, partialTick, size);
            }
            return;
        }
        original.call(poseStack, buffer, entity, weight, partialTick, level, size);
    }
    
}
