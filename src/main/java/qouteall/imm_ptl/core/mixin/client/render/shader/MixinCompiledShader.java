package qouteall.imm_ptl.core.mixin.client.render.shader;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import qouteall.imm_ptl.core.render.ShaderCodeTransformation;

import java.util.function.BiFunction;

@Mixin(GlDevice.class)
public class MixinCompiledShader {
    /**
     * The device compiles shaders itself and never calls GlShaderModule.compile.
     * So the clipping transformation is applied to the source that the device fetches.
     */
    @ModifyVariable(
        method = "getOrCompileShader",
        at = @At("HEAD"),
        argsOnly = true
    )
    private BiFunction<ResourceLocation, ShaderType, String> ip_transformShaderSource(
        BiFunction<ResourceLocation, ShaderType, String> shaderSource
    ) {
        return (id, type) -> {
            String code = shaderSource.apply(id, type);
            if (code == null) {
                return null;
            }
            return ShaderCodeTransformation.transform(type, id.toString(), code);
        };
    }
}
