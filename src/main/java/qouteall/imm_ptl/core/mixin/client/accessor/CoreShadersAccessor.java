package qouteall.imm_ptl.core.mixin.client.accessor;

import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.ShaderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(CoreShaders.class)
public interface CoreShadersAccessor {
    @Accessor("PROGRAMS")
    static List<ShaderProgram> getPrograms() {
        throw new RuntimeException();
    }
    
}
