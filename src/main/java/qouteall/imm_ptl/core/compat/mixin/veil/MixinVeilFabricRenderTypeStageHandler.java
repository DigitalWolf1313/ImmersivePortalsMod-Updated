package qouteall.imm_ptl.core.compat.mixin.veil;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;


@Mixin(targets = "foundry.veil.fabric.FabricRenderTypeStageHandler")
public abstract class MixinVeilFabricRenderTypeStageHandler {

    @ModifyVariable(
        method = "renderStage",
        at = @At("HEAD"),
        argsOnly = true,
        index = 10
    )
    private static Camera iPortals$FixNullCameraInRenderStage(Camera camera) {
        if (camera == null) {
            Camera fallback = Minecraft.getInstance().gameRenderer.getMainCamera();
            if (fallback != null) {
                return fallback;
            }
        }
        return camera;
    }
}