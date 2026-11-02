package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer_Lighting {
    @WrapOperation(
        method = {"getBlockLightLevel", "getSkyLightLevel"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I")
    )
    private int ip_sampleProjectionLight(
        Level level, LightLayer layer, BlockPos pos, Operation<Integer> original, Entity entity, BlockPos originalPos
    ) {
        CrossPortalEntityRenderer.ProjectionLightSample sample =
            CrossPortalEntityRenderer.getProjectionLightSample(level, pos, entity);
        if (sample == null) {
            return original.call(level, layer, pos);
        }
        return original.call(sample.level(), layer, sample.pos());
    }
}
