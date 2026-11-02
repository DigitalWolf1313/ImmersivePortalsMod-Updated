package qouteall.imm_ptl.core.mixin.client.accessor;

import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import qouteall.imm_ptl.core.ducks.IEEntityInterpolation;

@Mixin(Boat.class)
public interface IEBoatInterpolation extends IEEntityInterpolation {
    @Override
    @Accessor("lerpSteps")
    int ip_getLerpSteps();
    
    @Override
    @Accessor("lerpSteps")
    void ip_setLerpSteps(int steps);
}
