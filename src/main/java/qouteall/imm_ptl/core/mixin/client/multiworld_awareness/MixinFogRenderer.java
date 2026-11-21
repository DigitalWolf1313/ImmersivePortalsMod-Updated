package qouteall.imm_ptl.core.mixin.client.multiworld_awareness;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;

@Mixin(value = FogRenderer.class, priority = 1100)
public class MixinFogRenderer {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;
    @Shadow
    private static int targetBiomeFog = -1;
    @Shadow
    private static int previousBiomeFog = -1;
    @Shadow
    private static long biomeChangedTime = -1L;
    
    // Sampling at the destination keeps the fog and sky colors independent of the virtual camera.
    @WrapOperation(
        method = "Lnet/minecraft/client/renderer/FogRenderer;setupColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"
        )
    )
    private static Vec3 ip_wrapCameraPosInSetupColor(Camera camera, Operation<Vec3> original) {
        return FogRendererContext.getBiomeSamplingPos(original.call(camera));
    }
    
    static {
        FogRendererContext.copyContextFromObject = context -> {
            fogRed = context.red;
            fogGreen = context.green;
            fogBlue = context.blue;
            targetBiomeFog = context.targetBiomeFog;
            previousBiomeFog = context.previousBiomeFog;
            biomeChangedTime = context.biomeChangedTime;
        };
        
        FogRendererContext.copyContextToObject = context -> {
            context.red = fogRed;
            context.green = fogGreen;
            context.blue = fogBlue;
            context.targetBiomeFog = targetBiomeFog;
            context.previousBiomeFog = previousBiomeFog;
            context.biomeChangedTime = biomeChangedTime;
        };
        
        FogRendererContext.getCurrentFogColor =
            () -> new Vec3(fogRed, fogGreen, fogBlue);
        
        FogRendererContext.snapBiomeChangedTime = () -> {
            biomeChangedTime = -1L;
        };
        
        FogRendererContext.init();
    }
}
