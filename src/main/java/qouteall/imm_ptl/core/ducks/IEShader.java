package qouteall.imm_ptl.core.ducks;

import com.mojang.blaze3d.opengl.Uniform;
import org.jetbrains.annotations.Nullable;

public interface IEShader {
    int ip_getClippingEquationUniformLocation();
    
    int ip_getProgramId();
}
