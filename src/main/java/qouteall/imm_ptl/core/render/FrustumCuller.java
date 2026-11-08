package qouteall.imm_ptl.core.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.renderer.RendererUsingStencil;
import qouteall.q_misc_util.my_util.BoxPredicateF;
import qouteall.q_misc_util.my_util.Plane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Environment(EnvType.CLIENT)
public class FrustumCuller {
    private @Nullable BoxPredicateF canDetermineInvisibleFunc;
    private double camX;
    private double camY;
    private double camZ;
    
    public FrustumCuller() {
    }
    
    public void update(double cameraX, double cameraY, double cameraZ) {
        canDetermineInvisibleFunc = getCanDetermineInvisibleFunc(cameraX, cameraY, cameraZ);
        camX = cameraX;
        camY = cameraY;
        camZ = cameraZ;
    }
    
    public boolean canDetermineInvisibleWithCameraCoord(
        float minX, float minY, float minZ, float maxX, float maxY, float maxZ
    ) {
        if (canDetermineInvisibleFunc == null) {
            return false;
        }
        
        return canDetermineInvisibleFunc.test(
            minX, minY, minZ,
            maxX, maxY, maxZ
        );
    }
    
    public boolean canDetermineInvisibleWithCameraCoord(
        double minX, double minY, double minZ, double maxX, double maxY, double maxZ
    ) {
        if (canDetermineInvisibleFunc == null) {
            return false;
        }
        
        return canDetermineInvisibleFunc.test(
            (float) minX, (float) minY, (float) minZ,
            (float) maxX, (float) maxY, (float) maxZ
        );
    }
    
    public boolean canDetermineInvisibleWithWorldCoord(
        double minX, double minY, double minZ, double maxX, double maxY, double maxZ
    ) {
        if (canDetermineInvisibleFunc == null) {
            return false;
        }
        
        return canDetermineInvisibleWithCameraCoord(
            minX - camX, minY - camY, minZ - camZ,
            maxX - camX, maxY - camY, maxZ - camZ
        );
    }
    
    private @Nullable BoxPredicateF getCanDetermineInvisibleFunc(
        double cameraX,
        double cameraY,
        double cameraZ
    ) {
        if (!IPCGlobal.doUseAdvancedFrustumCulling) {
            return null;
        }
        if (IrisInterface.invoker.isRenderingShadowMap()) {
            return null;
        }
        
        Vec3 cameraPos = new Vec3(cameraX, cameraY, cameraZ);
        
        BoxPredicateF outerCuller = null;
        if (isOuterFrustumCullingAllowed()) {
            outerCuller = getOuterCullingFuncForNearbyPortals(cameraPos);
        }
        
        if (PortalRendering.isRendering()) {
            // do inner frustum culling
            
            BoxPredicateF innerCuller = getInnerCullingFuncWithOuterLayers(
                PortalRendering.getRenderingPortal(), cameraPos
            );
            
            // The portals inside the rendered world cull what is behind them too.
            return combineCullers(innerCuller, outerCuller);
        }
        
        return outerCuller;
    }
    
    private static boolean isOuterFrustumCullingAllowed() {
        if (!IPCGlobal.useSuperAdvancedFrustumCulling) {
            return false;
        }
        
        // don't do outer frustum culling when sodium is not present
        // in vanilla, it rebuilds visibility section list lazily
        // if the player moves but does not rotate camera, it won't update immediately
        // that will cause artifacts with outer frustum culling
        // the inner frustum culling is controlled by ImmPtl and is correct
        return SodiumInterface.invoker.isSodiumPresent();
    }
    
    private static @Nullable BoxPredicateF combineCullers(
        @Nullable BoxPredicateF a, @Nullable BoxPredicateF b
    ) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        
        // A box is culled when either culler culls it.
        return (float minX, float minY, float minZ, float maxX, float maxY, float maxZ) ->
            a.test(minX, minY, minZ, maxX, maxY, maxZ) ||
                b.test(minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    // Returns a func that culls whatever is outside the view through the innermost portal.
    // The frustums of the outer portals are applied too, because the world is only seen through all of them.
    private static @Nullable BoxPredicateF getInnerCullingFuncWithOuterLayers(
        Portal renderingPortal, Vec3 cameraPos
    ) {
        BoxPredicateF innermostCuller = renderingPortal.getPortalShape().getInnerFrustumCullingFunc(
            renderingPortal, cameraPos
        );
        
        List<Portal> path = PortalRendering.getPortalPath();
        
        List<Frustum4Planes> outerFrustums = new ArrayList<>();
        
        // The last portal in the path is the innermost one, which is handled by the innermost culler.
        for (int i = 0; i < path.size() - 1; i++) {
            Portal outerPortal = path.get(i);
            if (outerPortal.getPortalShape().isPlanar() && !outerPortal.isFuseView()) {
                outerFrustums.add(getOuterLayerFrustum(path, i, cameraPos));
            }
        }
        
        if (outerFrustums.isEmpty()) {
            return innermostCuller;
        }
        
        Frustum4Planes[] outerFrustumArray = outerFrustums.toArray(new Frustum4Planes[0]);
        
        // A box is culled when it is fully outside the frustum of any layer.
        return (float minX, float minY, float minZ, float maxX, float maxY, float maxZ) -> {
            if (innermostCuller != null &&
                innermostCuller.test(minX, minY, minZ, maxX, maxY, maxZ)
            ) {
                return true;
            }
            
            for (Frustum4Planes frustum : outerFrustumArray) {
                if (frustum.isFullyOutside(minX, minY, minZ, maxX, maxY, maxZ)) {
                    return true;
                }
            }
            
            return false;
        };
    }
    
    // Returns the frustum of the portal at the index, in the coordinate of the innermost rendered world.
    // All the planes pass through the camera, so only the portal vertices need to be transformed.
    private static Frustum4Planes getOuterLayerFrustum(
        List<Portal> path, int index, Vec3 cameraPos
    ) {
        Vec3[] vertices = getRectPortalFourVerticesCounterClockwise(
            path.get(index).getThisSideState()
        );
        
        int mirrorNum = 0;
        for (int i = index; i < path.size(); i++) {
            Portal portal = path.get(i);
            for (int k = 0; k < 4; k++) {
                vertices[k] = portal.transformPoint(vertices[k]);
            }
            if (portal instanceof Mirror) {
                mirrorNum++;
            }
        }
        
        Vec3[] relativeVertices = new Vec3[]{
            vertices[0].subtract(cameraPos),
            vertices[1].subtract(cameraPos),
            vertices[2].subtract(cameraPos),
            vertices[3].subtract(cameraPos)
        };
        
        if (mirrorNum % 2 == 1) {
            // Each mirror reverses the vertex order.
            relativeVertices = new Vec3[]{
                relativeVertices[3], relativeVertices[2], relativeVertices[1], relativeVertices[0]
            };
        }
        
        return getFrustumPlanesFromFourVerticesCounterClockwise(relativeVertices);
    }
    
    // Returns whether the geometry behind the portal can be culled.
    // That is only valid when the portal renderer draws the portal view over that geometry.
    private static boolean canOuterCullBehind(Portal portal, Vec3 cameraPos) {
        if (!portal.isInFrontOfPortal(cameraPos) || !portal.canDoOuterFrustumCulling()) {
            return false;
        }
        
        // The portal content is not rendered when the layer limit is reached.
        if (PortalRendering.getPortalLayer() >= PortalRendering.getMaxPortalLayer()) {
            return false;
        }
        
        if (!PortalRendering.isRendering()) {
            return true;
        }
        
        Portal renderingPortal = PortalRendering.getRenderingPortal();
        
        // The portal renderer skips these portals when rendering inside the rendering portal.
        if (renderingPortal.cannotRenderInMe(portal) ||
            PortalRendering.isInvalidRecursionRendering(portal) ||
            RendererUsingStencil.shouldSkipRenderingInsideFuseViewPortal(portal)
        ) {
            return false;
        }
        
        // The part of the portal behind the clipping plane is not drawn.
        // Culling behind a partly clipped portal would also remove the visible view behind the clipping plane.
        Plane clipping = PortalRendering.getActiveClippingPlane();
        if (clipping != null) {
            Vec3[] vertices = getRectPortalFourVerticesCounterClockwise(portal.getThisSideState());
            for (Vec3 vertex : vertices) {
                if (!clipping.isPointOnPositiveSide(vertex)) {
                    return false;
                }
            }
        }
        
        return true;
    }
        // The max number of portals whose hidden areas are culled at the same time.
    private static final int MAX_OUTER_CULLING_PORTAL_NUM = 32;
    
    private record ScoredPortal(Portal portal, double apparentSize) {}
    
    // Returns a func that culls whatever is hidden behind any of the nearby portals.
    private static @Nullable BoxPredicateF getOuterCullingFuncForNearbyPortals(Vec3 cameraPos) {
        if (TransformationManager.isIsometricView) {
            return null;
        }
        
        var world = Minecraft.getInstance().level;
        if (world == null) {
            return null;
        }
        
        List<Portal> portals = IPMcHelper.getNearbyPortalList(
            world, cameraPos, 28,
            portal -> canOuterCullBehind(portal, cameraPos)
        );
        if (portals.isEmpty()) {
            return null;
        }
        
        List<ScoredPortal> scoredPortals = new ArrayList<>(portals.size());
        for (Portal portal : portals) {
            scoredPortals.add(new ScoredPortal(portal, getApparentSize(portal, cameraPos)));
        }
        
        // The portals that cover more of the view are kept when there are too many.
        scoredPortals.sort((a, b) -> Double.compare(b.apparentSize(), a.apparentSize()));
        
        int num = Math.min(scoredPortals.size(), MAX_OUTER_CULLING_PORTAL_NUM);
        List<BoxPredicateF> cullers = new ArrayList<>(num);
        for (int i = 0; i < num; i++) {
            Portal portal = scoredPortals.get(i).portal();
            BoxPredicateF culler = portal.getPortalShape().getOuterFrustumCullingFunc(portal, cameraPos);
            if (culler != null) {
                cullers.add(culler);
            }
        }
        
        if (cullers.isEmpty()) {
            return null;
        }
        
        if (cullers.size() == 1) {
            return cullers.get(0);
        }
        
        BoxPredicateF[] cullerArray = cullers.toArray(new BoxPredicateF[0]);
        
        // A box is culled when it is hidden behind at least one of the portals.
        return (float minX, float minY, float minZ, float maxX, float maxY, float maxZ) -> {
            for (BoxPredicateF culler : cullerArray) {
                if (culler.test(minX, minY, minZ, maxX, maxY, maxZ)) {
                    return true;
                }
            }
            return false;
        };
    }
    
    // Approximates the solid angle that the portal covers in the camera's view.
    private static double getApparentSize(Portal portal, Vec3 cameraPos) {
        UnilateralPortalState state = portal.getThisSideState();
        double area = state.width() * state.height();
        
        Vec3 toPortal = portal.getOriginPos().subtract(cameraPos);
        double distance = Math.max(toPortal.length(), 0.01);
        double cos = Math.abs(toPortal.dot(portal.getNormal())) / distance;
        
        return area * cos / (distance * distance);
    }
    
    // 2  1
    // 3  0
    public static Vec3[] getRectPortalFourVerticesCounterClockwise(
        UnilateralPortalState thisSideState
    ) {
        double halfWidth = thisSideState.width() / 2;
        double halfHeight = thisSideState.height() / 2;
        return new Vec3[]{
            thisSideState.transformLocalToGlobal(halfWidth, -halfHeight, 0),
            thisSideState.transformLocalToGlobal(halfWidth, halfHeight, 0),
            thisSideState.transformLocalToGlobal(-halfWidth, halfHeight, 0),
            thisSideState.transformLocalToGlobal(-halfWidth, -halfHeight, 0)
        };
    }
    
    public static Frustum4Planes getFrustumPlanesFromFourVerticesCounterClockwise(
        Vec3[] vertices
    ) {
        // 2  1
        // 3  0
        Vec3 normal0 = vertices[1].cross(vertices[0]).normalize();
        Vec3 normal1 = vertices[2].cross(vertices[1]).normalize();
        Vec3 normal2 = vertices[3].cross(vertices[2]).normalize();
        Vec3 normal3 = vertices[0].cross(vertices[3]).normalize();
        
        // assume that the plane origin is in the coordinate origin, so W is 0
        return new Frustum4Planes(
            (float) normal0.x, (float) normal0.y, (float) normal0.z, 0,
            (float) normal1.x, (float) normal1.y, (float) normal1.z, 0,
            (float) normal2.x, (float) normal2.y, (float) normal2.z, 0,
            (float) normal3.x, (float) normal3.y, (float) normal3.z, 0
        );
    }
    
    // the frustum culling func returning true means culled
    public static BoxPredicateF getFlatPortalInnerFrustumCullingFunc(
        Portal portal, Vec3 cameraPos
    ) {
        Vec3[] v = getRectPortalFourVerticesCounterClockwise(portal.getThisSideState());
        
        // 2  1
        // 3  0
        Vec3[] vTransformed = new Vec3[]{
            portal.transformPoint(v[0]).subtract(cameraPos),
            portal.transformPoint(v[1]).subtract(cameraPos),
            portal.transformPoint(v[2]).subtract(cameraPos),
            portal.transformPoint(v[3]).subtract(cameraPos)
        };
        
        if (portal instanceof Mirror) {
            // flip for mirror
            vTransformed = new Vec3[]{
                vTransformed[3], vTransformed[2], vTransformed[1], vTransformed[0]
            };
        }
        
        Frustum4Planes fourPlanes =
            getFrustumPlanesFromFourVerticesCounterClockwise(vTransformed);
        
        // to cull out, it must be fully outside portal frustum
        return fourPlanes::isFullyOutside;
    }
    
    // the frustum culling func returning true means culled
    public static BoxPredicateF getFlatPortalOuterFrustumCullingFunc(
        Portal portal, Vec3 cameraPos
    ) {
        Vec3[] v = getRectPortalFourVerticesCounterClockwise(portal.getThisSideState());
        
        Vec3[] vTransformed = new Vec3[]{
            v[0].subtract(cameraPos),
            v[1].subtract(cameraPos),
            v[2].subtract(cameraPos),
            v[3].subtract(cameraPos)
        };
        
        Frustum4Planes fourPlanes =
            getFrustumPlanesFromFourVerticesCounterClockwise(vTransformed);
        
        Plane portalPlane = new Plane(
            portal.getOriginPos().subtract(cameraPos), portal.getNormal()
        );
        
        float portalPlaneX = (float) portalPlane.getEquationX();
        float portalPlaneY = (float) portalPlane.getEquationY();
        float portalPlaneZ = (float) portalPlane.getEquationZ();
        float portalPlaneW = (float) portalPlane.getEquationW();
        
        // to cull out, it must be fully behind portal plane
        // and inside portal frustum
        return (float minX, float minY, float minZ, float maxX, float maxY, float maxZ) -> {
            boolean a = isFullyBehindPlane(
                minX, minY, minZ, maxX, maxY, maxZ,
                portalPlaneX, portalPlaneY, portalPlaneZ, portalPlaneW
            );
            boolean b = fourPlanes.isFullyInside(
                minX, minY, minZ, maxX, maxY, maxZ
            );
            return a && b;
        };
    }
    
    // (p - origin) * normal > 0
    // p * normal - origin * normal > 0
    // planeX * x + planeY * y + planeZ * z + planeW > 0
    // planeX = normal.x  planeY = normal.y  planeZ = normal.z  planeW = -origin * normal
    public static boolean isFullyInFrontOfPlane(
        float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
        float planeX, float planeY, float planeZ, float planeW
    ) {
        float testingBoxX = planeX > 0 ? minX : maxX;
        float testingBoxY = planeY > 0 ? minY : maxY;
        float testingBoxZ = planeZ > 0 ? minZ : maxZ;
        
        return testingBoxX * planeX + testingBoxY * planeY + testingBoxZ * planeZ + planeW > 0;
    }
    
    public static boolean isFullyBehindPlane(
        float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
        float planeX, float planeY, float planeZ, float planeW
    ) {
        float testingBoxX = planeX > 0 ? maxX : minX;
        float testingBoxY = planeY > 0 ? maxY : minY;
        float testingBoxZ = planeZ > 0 ? maxZ : minZ;
        
        return testingBoxX * planeX + testingBoxY * planeY + testingBoxZ * planeZ + planeW < 0;
    }
    
    public record Frustum4Planes(
        float p0X, float p0Y, float p0Z, float p0W,
        float p1X, float p1Y, float p1Z, float p1W,
        float p2X, float p2Y, float p2Z, float p2W,
        float p3X, float p3Y, float p3Z, float p3W
    ) {
        public boolean isFullyOutside(
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ
        ) {
            return isFullyBehindPlane(
                minX, minY, minZ, maxX, maxY, maxZ,
                p0X, p0Y, p0Z, p0W
            ) || isFullyBehindPlane(
                minX, minY, minZ, maxX, maxY, maxZ,
                p1X, p1Y, p1Z, p1W
            ) || isFullyBehindPlane(
                minX, minY, minZ, maxX, maxY, maxZ,
                p2X, p2Y, p2Z, p2W
            ) || isFullyBehindPlane(
                minX, minY, minZ, maxX, maxY, maxZ,
                p3X, p3Y, p3Z, p3W
            );
        }
        
        public boolean isFullyInside(
            float minX, float minY, float minZ, float maxX, float maxY, float maxZ
        ) {
            return isFullyInFrontOfPlane(
                minX, minY, minZ, maxX, maxY, maxZ,
                p0X, p0Y, p0Z, p0W
            ) && isFullyInFrontOfPlane(
                minX, minY, minZ, maxX, maxY, maxZ,
                p1X, p1Y, p1Z, p1W
            ) && isFullyInFrontOfPlane(
                minX, minY, minZ, maxX, maxY, maxZ,
                p2X, p2Y, p2Z, p2W
            ) && isFullyInFrontOfPlane(
                minX, minY, minZ, maxX, maxY, maxZ,
                p3X, p3Y, p3Z, p3W
            );
        }
    }
    
    
    @Deprecated
    public static enum BatchTestResult {
        all_true,
        all_false,
        both
    }
    
    @Deprecated
    public static BatchTestResult testBoxTwoVertices(
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        double planeNormalX, double planeNormalY, double planeNormalZ,
        double planePosX, double planePosY, double planePosZ
    ) {
        double p1x;
        double p1y;
        double p1z;
        double p2x;
        double p2y;
        double p2z;
        
        if (planeNormalX > 0) {
            p1x = minX;
            p2x = maxX;
        }
        else {
            p1x = maxX;
            p2x = minX;
        }
        
        if (planeNormalY > 0) {
            p1y = minY;
            p2y = maxY;
        }
        else {
            p1y = maxY;
            p2y = minY;
        }
        
        if (planeNormalZ > 0) {
            p1z = minZ;
            p2z = maxZ;
        }
        else {
            p1z = maxZ;
            p2z = minZ;
        }
        
        boolean r1 = isInFrontOf(
            p1x - planePosX, p1y - planePosY, p1z - planePosZ,
            planeNormalX, planeNormalY, planeNormalZ
        );
        
        boolean r2 = isInFrontOf(
            p2x - planePosX, p2y - planePosY, p2z - planePosZ,
            planeNormalX, planeNormalY, planeNormalZ
        );
        
        if (r1 && r2) {
            return BatchTestResult.all_true;
        }
        
        if ((!r1) && (!r2)) {
            return BatchTestResult.all_false;
        }
        
        return BatchTestResult.both;
    }
    
    @Deprecated
    public static BatchTestResult testBoxTwoVertices(
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ,
        Vec3 planeNormal
    ) {
        return testBoxTwoVertices(
            minX, minY, minZ, maxX, maxY, maxZ,
            planeNormal.x, planeNormal.y, planeNormal.z,
            0, 0, 0
        );
    }
    
    @Deprecated
    private static boolean isInFrontOf(
        double x, double y, double z,
        double planeNormalX, double planeNormalY, double planeNormalZ
    ) {
        return x * planeNormalX + y * planeNormalY + z * planeNormalZ >= 0;
    }
}
