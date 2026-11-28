package qouteall.imm_ptl.core.mixin.client.render.shader;

import com.mojang.blaze3d.opengl.GlProgram;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEShader;

@Mixin(GlProgram.class)
public abstract class MixinShaderInstance implements IEShader {
    
    @Shadow
    @Final
    private int programId;
    
    @Unique
    private int ip_clippingEquationLoccation = -1;
    
    @Inject(
        method = "setupUniforms",
        at = @At("RETURN")
    )
    private void onSetupUniforms(CallbackInfo ci) {
        ip_clippingEquationLoccation = GL20.glGetUniformLocation(programId, "iportal_ClippingEquation");
    }
    
    @Override
    public int ip_getClippingEquationUniformLocation() {
        return ip_clippingEquationLoccation;
    }
    
    @Override
    public int ip_getProgramId() {
        return programId;
    }
}
