package qouteall.imm_ptl.core.mixin.client.accessor;

import net.minecraft.world.entity.vehicle.OldMinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OldMinecartBehavior.class)
public interface IEOldMinecartBehavior {
    @Accessor("lerpSteps")
    int ip_getLerpSteps();
    
    @Accessor("lerpSteps")
    void ip_setLerpSteps(int steps);
}
