package qouteall.imm_ptl.core.mixin.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface IEParticle {
    @Accessor("level")
    ClientLevel portal_getWorld();
    
    @Mutable
    @Accessor("level")
    void portal_setWorld(ClientLevel world);
    
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
    
    @Accessor("x")
    void portal_setX(double x);
    
    @Accessor("y")
    void portal_setY(double y);
    
    @Accessor("z")
    void portal_setZ(double z);
    
    @Accessor("xo")
    void portal_setXo(double xo);
    
    @Accessor("yo")
    void portal_setYo(double yo);
    
    @Accessor("zo")
    void portal_setZo(double zo);
}
