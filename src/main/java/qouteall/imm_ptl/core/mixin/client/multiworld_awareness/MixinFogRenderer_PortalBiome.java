package qouteall.imm_ptl.core.mixin.client.multiworld_awareness;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@Mixin(value = FogRenderer.class, priority = 1100)
public class MixinFogRenderer_PortalBiome {
    // Samples the underwater fog color from the biome at the portal's destination.
    @Redirect(
        method = "computeFogColor",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"
        )
    )
    private static Holder<Biome> ip_redirectGetBiomeInComputeFogColor(
        ClientLevel level, BlockPos pos
    ) {
        if (PortalRendering.isRendering()) {
            Portal portal = PortalRendering.getRenderingPortal();
            if (portal != null) {
                return level.getBiome(BlockPos.containing(portal.getDestPos()));
            }
        }
        return level.getBiome(pos);
    }
}
