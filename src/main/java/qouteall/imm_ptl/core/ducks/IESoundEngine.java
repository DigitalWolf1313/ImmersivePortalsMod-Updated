package qouteall.imm_ptl.core.ducks;

import net.minecraft.world.phys.Vec3;

import java.util.function.UnaryOperator;

public interface IESoundEngine {
    /**
     * Moves every playing and queued positional sound by the transformation.
     * Returns the number of sounds that were moved.
     */
    int ip_transformSoundPositions(UnaryOperator<Vec3> transformation);
    
    /**
     * Sets the listener transform immediately, without waiting for the next camera update.
     */
    void ip_setListenerTransform(Vec3 position, Vec3 forward, Vec3 up);
}
