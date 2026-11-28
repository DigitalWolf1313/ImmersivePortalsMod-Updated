package qouteall.imm_ptl.core.mixin.client.multiworld_awareness;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;

@Mixin(value = FogRenderer.class, priority = 1100)
public interface MixinFogRenderer {
    @Invoker("computeFogColor")
    Vector4f ip_computeFogColor(
        Camera camera, float partialTick, ClientLevel level,
        int renderDistance, float darkenWorldAmount, boolean isFoggy
    );
}
