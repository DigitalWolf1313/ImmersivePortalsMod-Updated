package qouteall.imm_ptl.core.mixin.client.accessor;

import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import qouteall.imm_ptl.core.ducks.IEEntityInterpolation;

@Mixin(Boat.class)
public interface IEBoatInterpolation extends IEEntityInterpolation {
    @Override
    @Accessor("lerpX")
    double ip_getLerpX();
    
    @Override
    @Accessor("lerpY")
    double ip_getLerpY();
    
    @Override
    @Accessor("lerpZ")
    double ip_getLerpZ();
    
    @Override
    @Accessor("lerpYRot")
    double ip_getLerpYRot();
    
    @Override
    @Accessor("lerpXRot")
    double ip_getLerpXRot();
    
    @Override
    @Accessor("lerpSteps")
    int ip_getLerpSteps();
    
    @Override
    @Accessor("lerpSteps")
    void ip_setLerpSteps(int steps);
}
