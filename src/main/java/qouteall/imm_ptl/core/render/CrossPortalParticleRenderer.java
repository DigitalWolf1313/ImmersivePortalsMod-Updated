package qouteall.imm_ptl.core.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.mixin.client.particle.IEParticle;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a particle that touches a portal on the other side of the portal as well.
 * A particle that has just moved through a portal is in the destination world,
 * and its part that still sticks out of the portal is drawn in the world it came from.
 * The part of the copy that is behind the destination plane is cut off by the portal clipping,
 * and outside of portal views it is hidden by the depth of the portal surface.
 */
public class CrossPortalParticleRenderer {
    private static final double CANDIDATE_RANGE = 64;
    private static final long REFRESH_INTERVAL_MS = 50;
    
    private static List<Portal> allPortals = new ArrayList<>();
    private static long lastRefreshTime = 0;
    
    // The portals whose destination is the world that is being rendered.
    private static final List<Portal> candidates = new ArrayList<>();
    
    /**
     * Collects the portals that lead into the world that is about to be rendered.
     */
    public static void beginPass(Camera camera) {
        candidates.clear();
        
        if (!IPGlobal.particlesCrossPortals) {
            return;
        }
        
        ClientLevel renderWorld = Minecraft.getInstance().level;
        if (renderWorld == null) {
            return;
        }
        
        refreshAllPortals();
        
        Vec3 cameraPos = camera.getPosition();
        for (Portal portal : allPortals) {
            if (portal.isRemoved() || portal.getDestDim() != renderWorld.dimension()) {
                continue;
            }
            
            // The copy of a particle is not scaled, so scaling portals are skipped.
            if (!portal.isVisible() || Math.abs(portal.getScaling() - 1.0) > 1e-6) {
                continue;
            }
            
            Vec3 cameraPosInSourceWorld = portal.inverseTransformPoint(cameraPos);
            if (portal.getDistanceToNearestPointInPortal(cameraPosInSourceWorld) > CANDIDATE_RANGE) {
                continue;
            }
            
            candidates.add(portal);
        }
    }
    
    private static void refreshAllPortals() {
        long now = Util.getMillis();
        if (now - lastRefreshTime < REFRESH_INTERVAL_MS) {
            return;
        }
        lastRefreshTime = now;
        
        List<Portal> result = new ArrayList<>();
        for (ClientLevel level : ClientWorldLoader.getClientWorlds()) {
            result.addAll(GlobalPortalStorage.getGlobalPortals(level));
            for (Entity entity : level.entitiesForRendering()) {
                if (entity instanceof Portal portal) {
                    result.add(portal);
                }
            }
        }
        allPortals = result;
    }
    
    /**
     * Returns the portal that the particle touches and that leads into the world being rendered.
     * Returns null when there is no such portal.
     */
    public static @Nullable Portal findGhostPortal(Particle particle, float partialTick) {
        if (candidates.isEmpty()) {
            return null;
        }
        
        IEParticle ieParticle = (IEParticle) particle;
        Level particleWorld = ieParticle.portal_getWorld();
        
        Vec3 center = new Vec3(
            Mth.lerp(partialTick, ieParticle.portal_getXo(), ieParticle.portal_getX()),
            Mth.lerp(partialTick, ieParticle.portal_getYo(), ieParticle.portal_getY()),
            Mth.lerp(partialTick, ieParticle.portal_getZo(), ieParticle.portal_getZ())
        );
        
        double radius = particle instanceof SingleQuadParticle quadParticle ?
            Math.max(quadParticle.getQuadSize(partialTick), 0.05) : 0.1;
        
        for (Portal portal : candidates) {
            if (portal.getOriginWorld() != particleWorld) {
                continue;
            }
            if (!portal.getBoundingBox().inflate(radius).contains(center)) {
                continue;
            }
            if (portal.getDistanceToNearestPointInPortal(center) > radius) {
                continue;
            }
            return portal;
        }
        
        return null;
    }
    
    /**
     * Draws the particle at its position transformed by the portal.
     * The particle is moved into the rendered world only while it is drawn.
     */
    public static void renderGhost(
        Particle particle, Portal portal, VertexConsumer vertexConsumer, Camera camera,
        float partialTick, Operation<Void> original
    ) {
        IEParticle ieParticle = (IEParticle) particle;
        
        ClientLevel oldWorld = ieParticle.portal_getWorld();
        double x = ieParticle.portal_getX();
        double y = ieParticle.portal_getY();
        double z = ieParticle.portal_getZ();
        double xo = ieParticle.portal_getXo();
        double yo = ieParticle.portal_getYo();
        double zo = ieParticle.portal_getZo();
        
        Vec3 newPos = portal.transformPoint(new Vec3(x, y, z));
        Vec3 newOldPos = portal.transformPoint(new Vec3(xo, yo, zo));
        
        if (PortalRendering.isRendering()) {
            Vec3 renderedCenter = newOldPos.lerp(newPos, partialTick);
            if (!RenderStates.isParticleOnDestinationSide(renderedCenter)) {
                return;
            }
        }
        
        try {
            // The light of the particle is read from the world that it is drawn in.
            ieParticle.portal_setWorld(Minecraft.getInstance().level);
            ieParticle.portal_setX(newPos.x);
            ieParticle.portal_setY(newPos.y);
            ieParticle.portal_setZ(newPos.z);
            ieParticle.portal_setXo(newOldPos.x);
            ieParticle.portal_setYo(newOldPos.y);
            ieParticle.portal_setZo(newOldPos.z);
            
            original.call(particle, vertexConsumer, camera, partialTick);
        }
        finally {
            ieParticle.portal_setWorld(oldWorld);
            ieParticle.portal_setX(x);
            ieParticle.portal_setY(y);
            ieParticle.portal_setZ(z);
            ieParticle.portal_setXo(xo);
            ieParticle.portal_setYo(yo);
            ieParticle.portal_setZo(zo);
        }
    }
}
