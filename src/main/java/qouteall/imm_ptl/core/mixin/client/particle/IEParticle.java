package qouteall.imm_ptl.core.mixin.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface IEParticle {
    @Accessor("level")
    ClientLevel portal_getWorld();
    
    @Accessor("x")
    double portal_getX();
    
    @Accessor("y")
    double portal_getY();
    
    @Accessor("z")
    double portal_getZ();
    
    @Accessor("xo")
    double portal_getXo();
    
    @Accessor("yo")
    double portal_getYo();
    
    @Accessor("zo")
    double portal_getZo();
}
