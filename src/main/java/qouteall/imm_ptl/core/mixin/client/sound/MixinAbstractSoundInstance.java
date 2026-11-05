package qouteall.imm_ptl.core.mixin.client.sound;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.ducks.IEAbstractSoundInstance;

@Mixin(AbstractSoundInstance.class)
public class MixinAbstractSoundInstance implements IEAbstractSoundInstance {
    @Shadow
    protected double x;
    
    @Shadow
    protected double y;
    
    @Shadow
    protected double z;
    
    @Override
    public void ip_setPos(Vec3 pos) {
        x = pos.x;
        y = pos.y;
        z = pos.z;
    }
}
