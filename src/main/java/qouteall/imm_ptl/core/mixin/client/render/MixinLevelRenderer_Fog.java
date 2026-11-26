package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.MyRenderHelper;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer_Fog {
    @WrapOperation(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/FogRenderer;setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;Lorg/joml/Vector4f;FZF)Lnet/minecraft/client/renderer/FogParameters;"
        )
    )
    private FogParameters transformPortalFogDistance(
        Camera camera, FogRenderer.FogMode fogMode, Vector4f fogColor, float renderDistance,
        boolean isFoggy, float partialTick, Operation<FogParameters> original
    ) {
        FogParameters fog = original.call(camera, fogMode, fogColor, renderDistance, isFoggy, partialTick);
        return new FogParameters(
            MyRenderHelper.transformFogDistance(fog.start()),
            MyRenderHelper.transformFogDistance(fog.end()),
            fog.shape(),
            fog.red(),
            fog.green(),
            fog.blue(),
            fog.alpha()
        );
    }
}
