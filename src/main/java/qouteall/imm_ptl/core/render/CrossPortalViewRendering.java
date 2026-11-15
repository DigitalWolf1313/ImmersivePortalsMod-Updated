package qouteall.imm_ptl.core.render;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.commands.PortalCommand;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;

// in third person view it may render cross portal.
// if view bobbing make the camera go through portal before the actual player go through portal,
//  it will also render cross-portal
public class CrossPortalViewRendering {
    public static final Minecraft client = Minecraft.getInstance();
    
    // if rendered, return true
    public static boolean renderCrossPortalView() {
        if (!IPGlobal.enableCrossPortalView) {
            return false;
        }
        
        Entity cameraEntity = client.cameraEntity;
        
        Camera camera1 = new Camera();
        float cameraY = ((IECamera) RenderStates.originalCamera).ip_getCameraY();
        float lastCameraY = ((IECamera) RenderStates.originalCamera).ip_getLastCameraY();
        ((IECamera) camera1).ip_setCameraY(cameraY, lastCameraY);
        Camera camera = camera1;
        camera.setup(
            client.level, cameraEntity,
            isThirdPerson(),
            isFrontView(),
            RenderStates.getPartialTick()
        );
        
        Vec3 realCameraPos = camera.getPosition();
        Vec3 isometricAdjustedOriginalCameraPos =
            TransformationManager.getIsometricAdjustedCameraPos(camera);
        
        Vec3 physicalPlayerHeadPos = ClientTeleportationManager.getPlayerEyePos(RenderStates.getPartialTick());
        
        Pair<Portal, Vec3> portalHit = PortalCommand.raytracePortals(
            client.level, physicalPlayerHeadPos, isometricAdjustedOriginalCameraPos, true
        ).orElse(null);
        
        if (portalHit == null) {
            return false;
        }
        
        Portal portal = portalHit.getFirst();
        Vec3 hitPos = portalHit.getSecond();
        
        if (!portal.canTeleportEntity(cameraEntity)) {
            return false;
        }
        
        Vec3 renderingCameraPos;
        
        if (isThirdPerson()) {
            double distance = getThirdPersonMaxDistance();
            
            Vec3 thirdPersonPos = realCameraPos.subtract(physicalPlayerHeadPos).normalize()
                .scale(distance).add(physicalPlayerHeadPos);
            
            renderingCameraPos = getThirdPersonCameraPos(thirdPersonPos, portal, hitPos);
        }
        else {
            renderingCameraPos = portal.transformPoint(realCameraPos);
        }
        
        ((IECamera) RenderStates.originalCamera).portal_setPos(renderingCameraPos);
        ((IECamera) RenderStates.originalCamera).ip_copyRotation(camera);
        
        WorldRenderInfo worldRenderInfo = new WorldRenderInfo.Builder()
            .setWorld(ClientWorldLoader.getWorld(portal.getDestDim()))
            .setCameraPos(renderingCameraPos)
            .setCameraTransformation(portal.getAdditionalCameraTransformation())
            .setOverwriteCameraTransformation(false)
            .setDescription(null)
            .setRenderDistance(client.options.getEffectiveRenderDistance())
            .setDoRenderHand(false)
            .setEnableViewBobbing(true)
            .build();
        
        IPCGlobal.renderer.invokeWorldRendering(worldRenderInfo);
        
        return true;
    }
    
    private static boolean isFrontView() {
        return client.options.getCameraType().isMirrored();
    }
    
    private static boolean isThirdPerson() {
        return !client.options.getCameraType().isFirstPerson();
    }
    
    /**
     * {@link Camera#getMaxZoom}
     */
    @SuppressWarnings("JavadocReference")
    private static Vec3 getThirdPersonCameraPos(Vec3 endPos, Portal portal, Vec3 startPos) {
        Vec3 rtStart = portal.transformPoint(startPos);
        Vec3 rtEnd = portal.transformPoint(endPos);
        Vec3 delta = rtEnd.subtract(rtStart);
        double length = delta.length();
        if (length < 1.0E-6) {
            return rtStart;
        }
        
        assert client.cameraEntity != null;
        double allowedDistance = length;
        
        // Mirrors vanilla's camera clipping by testing eight offset rays and keeping the shortest hit.
        for (int i = 0; i < 8; i++) {
            Vec3 offset = new Vec3(
                ((i & 1) * 2 - 1) * 0.1,
                (((i >> 1) & 1) * 2 - 1) * 0.1,
                (((i >> 2) & 1) * 2 - 1) * 0.1
            );
            Vec3 from = rtStart.add(offset);
            Vec3 to = rtEnd.add(offset);
            BlockHitResult hit = portal.getDestinationWorld().clip(
                new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, client.cameraEntity)
            );
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            
            double hitDistance = hit.getLocation().distanceTo(from);
            // A ray that starts inside the portal frame hits at distance zero and is ignored.
            if (hitDistance > 1.0E-3 && hitDistance < allowedDistance) {
                allowedDistance = hitDistance;
            }
        }
        
        return rtStart.add(delta.scale(allowedDistance / length));
    }
    
    private static double getThirdPersonMaxDistance() {
        return 4.0d * ScaleUtils.computeThirdPersonScale(client.player);
    }
    
    //    private static Vec3d getThirdPersonCameraPos(Portal portalHit, Camera resuableCamera) {
//        return CHelper.withWorldSwitched(
//            client.cameraEntity,
//            portalHit,
//            () -> {
//                World destinationWorld = portalHit.getDestinationWorld();
//                resuableCamera.update(
//                    destinationWorld,
//                    client.cameraEntity,
//                    true,
//                    isInverseView(),
//                    RenderStates.tickDelta
//                );
//                return resuableCamera.getPos();
//            }
//        );
//    }
}
