package qouteall.imm_ptl.core.compat.mixin.sodium;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.render.FrustumCuller;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class MixinSodiumWorldRenderer {
    @Inject(
        method = "setupTerrain",
        at = @At("HEAD")
    )
    private void onUpdateChunks(
        Camera camera, Viewport viewport, FogParameters fogParameters, boolean spectator,
        boolean updateChunksImmediately, ChunkRenderMatrices matrices, CallbackInfo ci
    ) {
        SodiumInterface.frustumCuller = new FrustumCuller();
        Vec3 cameraPos = camera.getPosition();
        SodiumInterface.frustumCuller.update(cameraPos.x, cameraPos.y, cameraPos.z);
    }
    
    @Inject(method = "getChunksDebugString", at = @At("RETURN"), cancellable = true)
    private void addPortalSectionCount(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(
            cir.getReturnValue() + " Portal Sections: " + RenderStates.lastPortalSectionsRendered
        );
    }
}
