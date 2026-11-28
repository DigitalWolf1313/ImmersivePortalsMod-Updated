
package qouteall.imm_ptl.core.mixin.client.accessor;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface IELivingEntityInterpolation {
    @Accessor("lerpHeadSteps")
    int ip_getLerpHeadSteps();
    
    @Accessor("lerpYHeadRot")
    double ip_getLerpYHeadRot();
    
    @Accessor("lerpYHeadRot")
    void ip_setLerpYHeadRot(double yaw);
}
