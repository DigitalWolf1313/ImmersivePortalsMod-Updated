package qouteall.imm_ptl.core.mixin.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.teleportation.ParticleTeleportation;

import java.util.List;

@Mixin(Particle.class)
public abstract class MixinParticle {
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
     */
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void portal_onMove(double dx, double dy, double dz, CallbackInfo ci) {
        List<Portal> portals = ParticleTeleportation.getPortals(level);
        if (portals.isEmpty() || !ParticleTeleportation.canCrossMore()) {
            return;
        }
        if (dx == 0 && dy == 0 && dz == 0) {
            return;
        }
        
        AABB box = getBoundingBox();
        Vec3 from = box.getCenter();
        Vec3 to = from.add(dx, dy, dz);
        AABB sweptBox = box.expandTowards(dx, dy, dz).inflate(0.1);
        
        Portal hitPortal = null;
        Vec3 hitPos = null;
        double hitDistanceSq = Double.MAX_VALUE;
        
        for (Portal portal : portals) {
            if (!portal.getBoundingBox().intersects(sweptBox)) {
                continue;
            }
            
            Vec3 hit = portal.rayTrace(from, to);
            if (hit == null) {
                continue;
            }
            
            double distanceSq = hit.distanceToSqr(from);
            if (distanceSq < hitDistanceSq) {
                hitPortal = portal;
                hitPos = hit;
                hitDistanceSq = distanceSq;
            }
        }
        
        if (hitPortal == null) {
            return;
        }
        
        ClientLevel destLevel = level;
        if (hitPortal.getDestDim() != level.dimension()) {
            destLevel = ClientWorldLoader.getOptionalWorld(hitPortal.getDestDim());
            if (destLevel == null || !ParticleTeleportation.isDimensionTicked(hitPortal.getDestDim())) {
                // The particle then collides with the portal's surroundings like it did without a portal.
                return;
            }
        }
        
        Vec3 oldVelocity = new Vec3(xd, yd, zd);
        Vec3 newVelocity = hitPortal.transformLocalVec(oldVelocity);
        Vec3 newRemainingMotion = hitPortal.transformLocalVec(to.subtract(hitPos));
        
        // The content direction in the destination points away from the destination portal.
        // Moving the particle slightly along it keeps the particle from hitting the reverse portal.
        Vec3 exitCenter = hitPortal.transformPoint(hitPos)
            .add(hitPortal.getContentDirection().normalize().scale(0.002));
        
        // The previous position is transformed too, so that render interpolation stays continuous.
        Vec3 oldCenter = new Vec3(xo, yo + bbHeight / 2, zo);
        Vec3 newOldCenter = hitPortal.transformPoint(oldCenter);
        
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
