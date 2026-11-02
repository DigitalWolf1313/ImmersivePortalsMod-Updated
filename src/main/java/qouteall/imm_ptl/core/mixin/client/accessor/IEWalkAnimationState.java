package qouteall.imm_ptl.core.mixin.client.accessor;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface IEWalkAnimationState {
    @Accessor("speedOld")
    float ip_getSpeedOld();
    
    @Accessor("speedOld")
    void ip_setSpeedOld(float speedOld);
    
    @Accessor("speed")
    void ip_setSpeed(float speed);
    
    @Accessor("position")
    float ip_getPosition();
    
    @Accessor("position")
    void ip_setPosition(float position);
}
