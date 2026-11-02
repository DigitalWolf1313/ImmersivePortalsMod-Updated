package qouteall.imm_ptl.core.mixin.client.accessor;

import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartBehavior;
import net.minecraft.world.entity.vehicle.NewMinecartBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.ducks.IEEntityInterpolation;

@Mixin(AbstractMinecart.class)
public abstract class IEMinecartInterpolation implements IEEntityInterpolation {
    @Shadow
    public abstract MinecartBehavior getBehavior();
    
    // The old behavior stores a step counter.
    // The new behavior interpolates through a step list and has no counter to expose.
    @Override
    public int ip_getLerpSteps() {
        MinecartBehavior behavior = getBehavior();
        if (behavior instanceof IEOldMinecartBehavior old) {
            return old.ip_getLerpSteps();
        }
        if (behavior instanceof NewMinecartBehavior newBehavior) {
            return newBehavior.lerpSteps.size();
        }
        return 0;
    }
    
    // Setting the step count has no effect on the new behavior.
    @Override
    public void ip_setLerpSteps(int steps) {
        if (getBehavior() instanceof IEOldMinecartBehavior old) {
            old.ip_setLerpSteps(steps);
        }
    }
}
