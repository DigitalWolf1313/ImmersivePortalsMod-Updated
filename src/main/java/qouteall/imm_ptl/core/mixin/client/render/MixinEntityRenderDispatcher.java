package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;
import qouteall.imm_ptl.core.render.context_management.RenderStates;


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
        method = "render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/entity/state/EntityRenderState;distanceToCameraSq:D",
            opcode = Opcodes.GETFIELD
        )
    )
    private double ip_projectionShadowDistance(EntityRenderState state, Operation<Double> original) {
        if (CrossPortalEntityRenderer.isRenderingEntityProjection) {
            Vec3 pos = CrossPortalEntityRenderer.transformForProjection(new Vec3(state.x, state.y, state.z));
            return ((EntityRenderDispatcher) (Object) this).distanceToSqr(pos.x, pos.y, pos.z);
        }
        return original.call(state);
    }
    
    // The vanilla shadow would sample the destination world at the entity's source coordinates.
    @WrapOperation(
        method = "render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FLnet/minecraft/world/level/LevelReader;F)V"
        )
    )
    private static void ip_renderProjectionShadow(
        PoseStack poseStack, MultiBufferSource buffer, EntityRenderState state, float weight,
        LevelReader level, float size, Operation<Void> original
    ) {
        if (CrossPortalEntityRenderer.isRenderingEntityProjection) {
            // The Iris shadow map has no circle shadows.
            if (!IrisInterface.invoker.isRenderingShadowMap()) {
                CrossPortalEntityRenderer.renderProjectionShadow(buffer, weight, RenderStates.getPartialTick(), size);
            }
            return;
        }
        original.call(poseStack, buffer, state, weight, level, size);
    }
    
}
