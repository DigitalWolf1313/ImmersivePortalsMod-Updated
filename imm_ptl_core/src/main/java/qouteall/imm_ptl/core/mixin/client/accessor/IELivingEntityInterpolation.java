
package qouteall.imm_ptl.core.mixin.client.accessor;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import qouteall.imm_ptl.core.ducks.IEEntityInterpolation;

@Mixin(LivingEntity.class)
public interface IELivingEntityInterpolation extends IEEntityInterpolation {
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
    
    @Accessor("lerpHeadSteps")
    int ip_getLerpHeadSteps();
    
    @Accessor("lyHeadRot")
    double ip_getLerpYHeadRot();
    
    @Accessor("lyHeadRot")
    void ip_setLerpYHeadRot(double yaw);
}
