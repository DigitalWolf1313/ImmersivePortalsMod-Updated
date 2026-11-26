package qouteall.imm_ptl.core.mixin.client.multiworld_awareness;

import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;

@Mixin(value = FogRenderer.class, priority = 1100)
public interface MixinFogRenderer {
    @Accessor("targetBiomeFog")
    static int ip_getTargetBiomeFog() {
        throw new RuntimeException();
    }

    @Accessor("targetBiomeFog")
    static void ip_setTargetBiomeFog(int value) {
        throw new RuntimeException();
    }

    @Accessor("previousBiomeFog")
    static int ip_getPreviousBiomeFog() {
        throw new RuntimeException();
    }

    @Accessor("previousBiomeFog")
    static void ip_setPreviousBiomeFog(int value) {
        throw new RuntimeException();
    }

    @Accessor("biomeChangedTime")
    static long ip_getBiomeChangedTime() {
        throw new RuntimeException();
    }

    @Accessor("biomeChangedTime")
    static void ip_setBiomeChangedTime(long value) {
        throw new RuntimeException();
    }
}
