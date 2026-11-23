package qouteall.imm_ptl.core.mixin.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.ducks.IEParticleTickStamp;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.teleportation.ParticleTeleportation;

import java.util.List;

@Mixin(Particle.class)
public abstract class MixinParticle implements IEParticleTickStamp {
    @Unique
    private int portal_lastTickStamp = -1;
    
    @Override
    public int portal_getLastTickStamp() {
        return portal_lastTickStamp;
    }
    
    @Override
    public void portal_setLastTickStamp(int stamp) {
        portal_lastTickStamp = stamp;
    }
    
    @Shadow
    @Final
    @Mutable
    protected ClientLevel level;
    
    @Shadow
    protected double x;
    @Shadow
    protected double y;
    @Shadow
    protected double z;
    
    @Shadow
    protected double xo;
    @Shadow
    protected double yo;
    @Shadow
    protected double zo;
    
    @Shadow
    protected double xd;
    @Shadow
    protected double yd;
    @Shadow
    protected double zd;
    
    @Shadow
    protected float bbHeight;
    
    @Shadow
    public abstract AABB getBoundingBox();
    
    @Shadow
    public abstract void setPos(double x, double y, double z);
    
    @Shadow
    public abstract void move(double x, double y, double z);
    
    /**
     * The portal check runs before the collision code,
     * so a particle passes through the wall or frame behind a portal.
     * The remaining motion is applied after the crossing, so collision then happens in the destination.
     * An animating portal can also swallow a particle that does not move.
     */
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void portal_onMove(double dx, double dy, double dz, CallbackInfo ci) {
        List<Portal> portals = ParticleTeleportation.getPortals(level);
        if (portals.isEmpty() || !ParticleTeleportation.canCrossMore()) {
            return;
        }
        
        AABB box = getBoundingBox();
        Vec3 from = box.getCenter();
        Vec3 to = from.add(dx, dy, dz);
        Vec3 velocity = new Vec3(xd, yd, zd);
        Vec3 oldCenter = new Vec3(xo, yo + bbHeight / 2, zo);
        
        ParticleTeleportation.Crossing crossing = null;
        for (Portal portal : portals) {
            ParticleTeleportation.Crossing candidate =
                ParticleTeleportation.check(portal, box, from, to, velocity, oldCenter);
            if (candidate != null && (crossing == null || candidate.progress() < crossing.progress())) {
                crossing = candidate;
            }
        }
        
        if (crossing == null) {
            return;
        }
        
        Portal hitPortal = crossing.portal();
        
        ClientLevel destLevel = level;
        if (hitPortal.getDestDim() != level.dimension()) {
            destLevel = ClientWorldLoader.getOptionalWorld(hitPortal.getDestDim());
            if (destLevel == null || !ParticleTeleportation.isDimensionTicked(hitPortal.getDestDim())) {
                // The particle then collides with the portal's surroundings like it did without a portal.
                return;
            }
        }
        
        Vec3 exitCenter = crossing.exitCenter();
        Vec3 newOldCenter = crossing.newOldCenter();
        Vec3 newVelocity = crossing.newVelocity();
        Vec3 newRemainingMotion = crossing.newRemainingMotion();
        
        level = destLevel;
        setPos(exitCenter.x, exitCenter.y - bbHeight / 2, exitCenter.z);
        xo = newOldCenter.x;
        yo = newOldCenter.y - bbHeight / 2;
        zo = newOldCenter.z;
        xd = newVelocity.x;
        yd = newVelocity.y;
        zd = newVelocity.z;
        
        ParticleTeleportation.enterCrossing();
        try {
            move(newRemainingMotion.x, newRemainingMotion.y, newRemainingMotion.z);
        }
        finally {
            ParticleTeleportation.exitCrossing();
        }
        
        ci.cancel();
    }
}
