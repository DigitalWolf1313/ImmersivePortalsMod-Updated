package qouteall.imm_ptl.core.compat.mixin.iris;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderPass;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.compat.iris_compatibility.ExperimentalIrisPortalRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

import java.nio.IntBuffer;

@Mixin(value = CompositeRenderer.class, remap = false)
public class MixinIrisCompositeRenderer {
    @WrapOperation(
        method = "renderAll",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V"
        )
    )
    private void iPortals$skipScaledPortalCompositePass(
        RenderPass renderPass, int firstIndex, int index, int indexCount, int primCount,
        Operation<Void> original
    ) {
        if (IPCGlobal.renderer instanceof ExperimentalIrisPortalRenderer
            && PortalRendering.isRendering()) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer viewport = stack.mallocInt(4);
                GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, viewport);
                var mainTarget = Minecraft.getInstance().getMainRenderTarget();
                if (viewport.get(2) != mainTarget.width || viewport.get(3) != mainTarget.height) {
                    return;
                }
            }
        }
        original.call(renderPass, firstIndex, index, indexCount, primCount);
    }
}
