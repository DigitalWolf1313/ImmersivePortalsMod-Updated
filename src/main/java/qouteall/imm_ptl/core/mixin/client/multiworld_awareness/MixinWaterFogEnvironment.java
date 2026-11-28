package qouteall.imm_ptl.core.mixin.client.multiworld_awareness;

import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WaterFogEnvironment.class)
public interface MixinWaterFogEnvironment {
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
