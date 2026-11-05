package qouteall.imm_ptl.core.compat.mixin.veil;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GLCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import qouteall.imm_ptl.core.render.ShaderCodeTransformation;

import java.util.Map;

@Pseudo
@Mixin(targets = "foundry.veil.impl.client.render.shader.processor.VanillaShaderProcessor", remap = false)
public abstract class MixinVeilVanillaShaderProcessor {
    private static final String CORE_SHADER_PREFIX = "shaders/core/";
    
    // Veil recompiles vanilla shaders from their raw resources and relinks the program.
    // That path skips Program.compileShaderInternal, so the clipping transformation has to be applied here.
    @WrapMethod(method = "modify")
    private static String ip$transformBeforeVeil(
        Map<String, Object> customProgramData,
        @Nullable String shaderInstance,
        @Nullable ResourceLocation name,
        @Nullable VertexFormat vertexFormat,
        int activeBuffers,
        int type,
        String source,
        GLCapabilities glCapabilities,
        Operation<String> original
    ) {
        String shaderId = ip$getShaderId(name);
        
        if (shaderId != null) {
            Program.Type programType = type == GL20C.GL_VERTEX_SHADER ? Program.Type.VERTEX : Program.Type.FRAGMENT;
            source = ShaderCodeTransformation.transform(programType, shaderId, source);
        }
        
        return original.call(
            customProgramData, shaderInstance, name, vertexFormat, activeBuffers, type, source, glCapabilities
        );
    }
    
    // Returns the id that the transformation configs use, which is the shader name without directory and extension.
    // Returns null for shaders outside the core shader folder.
    @Nullable
    private static String ip$getShaderId(@Nullable ResourceLocation path) {
        if (path == null) {
            return null;
        }
        
        String filePath = path.getPath();
        if (!filePath.startsWith(CORE_SHADER_PREFIX)) {
            return null;
        }
        
        String shaderName = filePath.substring(CORE_SHADER_PREFIX.length());
        int extensionIndex = shaderName.lastIndexOf('.');
        if (extensionIndex >= 0) {
            shaderName = shaderName.substring(0, extensionIndex);
        }
        
        return path.getNamespace().equals("minecraft") ? shaderName : path.getNamespace() + ":" + shaderName;
    }
}
