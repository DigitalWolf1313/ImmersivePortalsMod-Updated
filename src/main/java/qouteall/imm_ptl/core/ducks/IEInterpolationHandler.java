package qouteall.imm_ptl.core.ducks;

import net.minecraft.world.phys.Vec3;

public interface IEInterpolationHandler {
    /**
     * Returns the number of interpolation ticks left.
     */
    int ip_getRemainingSteps();
    
    /**
     * Interpolates with the given step count and keeps the handler's configured step count unchanged.
     * A step count of zero snaps the entity to the target.
     */
    void ip_interpolateTo(Vec3 pos, float yRot, float xRot, int steps);
}
