package qouteall.imm_ptl.core.mixin.client.multiworld_awareness;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;

@Mixin(value = FogRenderer.class, priority = 1100)
public class MixinFogRenderer_PortalBiome {
    // Sampling at the destination keeps the fog colors independent of the virtual camera.
    @WrapOperation(
        method = "computeFogColor",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private static Vec3 ip_wrapCameraPosInComputeFogColor(Camera camera, Operation<Vec3> original) {
        return FogRendererContext.getBiomeSamplingPos(original.call(camera));
    }
}
