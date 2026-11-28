package qouteall.imm_ptl.core.mixin.client.render;

import com.mojang.blaze3d.opengl.GlRenderPass;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlRenderPass.class)
public interface IEGlRenderPass {
    @Accessor("pipeline")
    GlRenderPipeline ip_getPipeline();
}
