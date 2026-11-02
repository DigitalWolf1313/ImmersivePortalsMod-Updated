package qouteall.imm_ptl.core.mixin.client.accessor;

import net.minecraft.world.entity.vehicle.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import qouteall.imm_ptl.core.ducks.IEEntityInterpolation;

@Mixin(AbstractMinecart.class)
public interface IEMinecartInterpolation extends IEEntityInterpolation {
    @Override
    @Accessor("lx")
    double ip_getLerpX();
    
    @Override
    @Accessor("ly")
    double ip_getLerpY();
    
    @Override
    @Accessor("lz")
    double ip_getLerpZ();
    
    @Override
    @Accessor("lyr")
    double ip_getLerpYRot();
    
    @Override
    @Accessor("lxr")
    double ip_getLerpXRot();
    
    @Override
    @Accessor("lSteps")
    int ip_getLerpSteps();
    
    @Override
    @Accessor("lSteps")
    void ip_setLerpSteps(int steps);
}
