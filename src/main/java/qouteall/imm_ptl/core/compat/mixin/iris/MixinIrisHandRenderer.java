package qouteall.imm_ptl.core.compat.mixin.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.pathways.HandRenderer;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;

/**
 * When rendering a same-dimension portal, Immersive Portals forces
 * {@link Camera#isDetached()} to return {@code true} so that the player's own body
 * renders through the portal (see {@code MixinCamera.onIsThirdPerson}).
 * Iris's {@link HandRenderer#canRender} however skips the hand for detached cameras,
 * so the hand disappears through same-dimension portals when a shader pack is active.
 * Cross-dimension portals are unaffected because the camera is never detached there.
 */
@Mixin(value = HandRenderer.class, remap = false)
public class MixinIrisHandRenderer {
    @WrapOperation(
        method = "canRender",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;isDetached()Z"
        )
    )
    private boolean iPortals$notDetachedWhenRenderingPlayerThroughPortal(
        Camera camera, Operation<Boolean> original
    ) {
        if (CrossPortalEntityRenderer.shouldRenderPlayerDefault()) {
            return false;
        }
        return original.call(camera);
    }
}