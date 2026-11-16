package qouteall.imm_ptl.core.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.portal.shape.BoxPortalShape;

/**
 * Finds the sections in the destination dimension that the opening of a flat portal touches.
 * The opening is treated as an oriented box that is padded along its normal.
 * Each section in the bounding range of that box is tested with the separating axis theorem.
 */
@Environment(EnvType.CLIENT)
public class PortalOpeningSections {
    // The opening box is expanded by this many blocks on each side of the plane.
    // This also makes the sections that only touch the plane count as part of the opening.
    private static final double PLANE_MARGIN = 0.5;
    
    private static final int MAX_CANDIDATE_SECTIONS = 4096;
    
    /**
     * Returns the packed section positions that intersect the opening in the destination dimension.
     * Returns null when the portal is not flat or its opening is too large to enumerate.
     */
    public static long @Nullable [] getOpeningSections(Portal portal) {
        if (portal.getPortalShape() instanceof BoxPortalShape) {
            return null;
        }
        
        UnilateralPortalState state = portal.getOtherSideState();
        
        Vec3 center = state.position();
        Vec3 axisW = state.getAxisW();
        Vec3 axisH = state.getAxisH();
        Vec3 normal = state.getNormal();
        
        double halfW = state.width() / 2;
        double halfH = state.height() / 2;
        double halfN = state.thickness() / 2 + PLANE_MARGIN;
        
        if (!Double.isFinite(halfW) || !Double.isFinite(halfH) || !Double.isFinite(center.x)
            || !Double.isFinite(center.y) || !Double.isFinite(center.z)) {
            return null;
        }
        
        double[][] obbAxes = {
            {axisW.x, axisW.y, axisW.z},
            {axisH.x, axisH.y, axisH.z},
            {normal.x, normal.y, normal.z}
        };
        double[] halfExtents = {halfW, halfH, halfN};
        double[] c = {center.x, center.y, center.z};
        
        int[] minSec = new int[3];
        int[] maxSec = new int[3];
        long candidateCount = 1;
        for (int i = 0; i < 3; i++) {
            double extent = Math.abs(obbAxes[0][i]) * halfW
                + Math.abs(obbAxes[1][i]) * halfH
                + Math.abs(obbAxes[2][i]) * halfN;
            minSec[i] = SectionPos.blockToSectionCoord(c[i] - extent);
            maxSec[i] = SectionPos.blockToSectionCoord(c[i] + extent);
            candidateCount *= (maxSec[i] - minSec[i] + 1);
            if (candidateCount > MAX_CANDIDATE_SECTIONS) {
                return null;
            }
        }
        
        // The separating axes are the three section axes, the three opening axes,
        // and their nine cross products.
        double[][] separatingAxes = new double[15][];
        int axisCount = 0;
        for (int i = 0; i < 3; i++) {
            double[] worldAxis = new double[3];
            worldAxis[i] = 1;
            separatingAxes[axisCount++] = worldAxis;
        }
        for (int j = 0; j < 3; j++) {
            separatingAxes[axisCount++] = obbAxes[j];
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double[] a = obbAxes[j];
                double[] cross = switch (i) {
                    case 0 -> new double[]{0, -a[2], a[1]};
                    case 1 -> new double[]{a[2], 0, -a[0]};
                    default -> new double[]{-a[1], a[0], 0};
                };
                if (cross[0] * cross[0] + cross[1] * cross[1] + cross[2] * cross[2] < 1e-9) {
                    continue;
                }
                separatingAxes[axisCount++] = cross;
            }
        }
        
        // These values are computed once per axis and shared by all sections.
        double[] openingRadius = new double[axisCount];
        double[] sectionRadius = new double[axisCount];
        double[] centerProjection = new double[axisCount];
        for (int k = 0; k < axisCount; k++) {
            double[] l = separatingAxes[k];
            double r = 0;
            for (int j = 0; j < 3; j++) {
                r += halfExtents[j] * Math.abs(l[0] * obbAxes[j][0] + l[1] * obbAxes[j][1] + l[2] * obbAxes[j][2]);
            }
            openingRadius[k] = r;
            sectionRadius[k] = 8 * (Math.abs(l[0]) + Math.abs(l[1]) + Math.abs(l[2]));
            centerProjection[k] = l[0] * c[0] + l[1] * c[1] + l[2] * c[2];
        }
        
        long[] result = new long[(int) candidateCount];
        int resultCount = 0;
        
        for (int sx = minSec[0]; sx <= maxSec[0]; sx++) {
            for (int sy = minSec[1]; sy <= maxSec[1]; sy++) {
                for (int sz = minSec[2]; sz <= maxSec[2]; sz++) {
                    double bx = sx * 16 + 8;
                    double by = sy * 16 + 8;
                    double bz = sz * 16 + 8;
                    
                    boolean separated = false;
                    for (int k = 0; k < axisCount; k++) {
                        double[] l = separatingAxes[k];
                        double dist = Math.abs(l[0] * bx + l[1] * by + l[2] * bz - centerProjection[k]);
                        if (dist > openingRadius[k] + sectionRadius[k] + 1e-6) {
                            separated = true;
                            break;
                        }
                    }
                    
                    if (!separated) {
                        result[resultCount++] = SectionPos.asLong(sx, sy, sz);
                    }
                }
            }
        }
        
        if (resultCount == result.length) {
            return result;
        }
        return java.util.Arrays.copyOf(result, resultCount);
    }
}
