package qouteall.imm_ptl.core.ducks;

import net.minecraft.world.phys.Vec3;

import java.util.function.UnaryOperator;

public interface IESoundEngine {
    int ip_transformSoundPositions(UnaryOperator<Vec3> transformation);

    void ip_setListenerTransform(Vec3 position, Vec3 forward, Vec3 up);
}
