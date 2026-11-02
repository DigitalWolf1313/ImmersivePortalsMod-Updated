
package qouteall.imm_ptl.core.mixin.client.accessor;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import qouteall.imm_ptl.core.ducks.IEEntityInterpolation;

@Mixin(LivingEntity.class)
public interface IELivingEntityInterpolation extends IEEntityInterpolation {
    @Override
    @Accessor("lerpSteps")
    int ip_getLerpSteps();
    
    @Override
    @Accessor("lerpSteps")
    void ip_setLerpSteps(int steps);
    
    @Accessor("lerpHeadSteps")
    int ip_getLerpHeadSteps();
    
    @Accessor("lerpYHeadRot")
    double ip_getLerpYHeadRot();
    
    @Accessor("lerpYHeadRot")
    void ip_setLerpYHeadRot(double yaw);
}
