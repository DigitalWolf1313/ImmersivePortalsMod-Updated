package qouteall.imm_ptl.core.compat.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.render.viewport.frustum.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;

@Mixin(value = Viewport.class, remap = false)
public class MixinSodiumViewport {
    @Redirect(
        method = "isBoxVisible",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/viewport/frustum/Frustum;testSection(FFF)Z"
        )
    )
    private boolean redirectTestSection(
        Frustum instance,
        float centerX, float centerY, float centerZ
    ) {
        boolean inFrustum = instance.testSection(centerX, centerY, centerZ);
        
        if (inFrustum) {
            if (SodiumInterface.frustumCuller != null) {
                float radius = Viewport.CHUNK_SECTION_PADDED_RADIUS;
                boolean canDetermineInvisible =
                    SodiumInterface.frustumCuller.canDetermineInvisibleWithCameraCoord(
                        centerX - radius, centerY - radius, centerZ - radius,
                        centerX + radius, centerY + radius, centerZ + radius
                    );
                return !canDetermineInvisible;
            }
        }
        
        return inFrustum;
    }
    
    // Sodium uses this test for the sections around the camera section, which are not found by the graph traversal.
    @Redirect(
        method = "isBoxVisibleLooser",
        at = @At(
            value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/viewport/frustum/Frustum;testSectionExpanded(FFFF)Z"
        )
    )
    private boolean redirectTestSectionExpanded(
        Frustum instance,
        float centerX, float centerY, float centerZ, float extra
    ) {
        boolean inFrustum = instance.testSectionExpanded(centerX, centerY, centerZ, extra);
        
        if (inFrustum) {
            if (SodiumInterface.frustumCuller != null) {
                // The expanded radius is the padded radius plus the extra margin.
                float radius = Viewport.CHUNK_SECTION_PADDED_RADIUS + extra;
                boolean canDetermineInvisible =
                    SodiumInterface.frustumCuller.canDetermineInvisibleWithCameraCoord(
                        centerX - radius, centerY - radius, centerZ - radius,
                        centerX + radius, centerY + radius, centerZ + radius
                    );
                return !canDetermineInvisible;
            }
        }
        
        return inFrustum;
    }
}
