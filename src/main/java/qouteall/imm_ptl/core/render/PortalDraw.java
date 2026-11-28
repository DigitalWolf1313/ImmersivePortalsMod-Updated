package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * Draws a mesh with a custom pipeline in its own render pass.
 */
public class PortalDraw {
    private static boolean depthAlways = false;
    
    /**
     * Called by the command encoder mixin after the pipeline state of a draw has been applied.
     * Pipelines have no depth function that always passes, so it is forced here.
     */
    public static void afterSetup() {
        if (depthAlways) {
            GlStateManager._depthFunc(GL11.GL_ALWAYS);
        }
    }
    
    /**
     * Draws the mesh into the target.
     * The mesh is closed afterwards.
     * The render pass unbinds the framebuffer when it ends, so this rebinds the target for the raw GL code that follows.
     * The stencil state, depth range and cull face mode are not part of a pipeline, so they stay as the raw GL code set them.
     *
     * @param projection  the projection uniform, or null to use the currently bound one
     * @param setup       binds extra uniforms and samplers, and may change the viewport, before the draw
     * @param clip        whether the portal clipping plane is applied to this draw
     * @param depthAlways whether the depth test always passes while the depth write still follows the pipeline
     */
    public static void drawMesh(
        String label, RenderPipeline pipeline, MeshData meshData, RenderTarget target,
        Matrix4f modelView, @Nullable GpuBufferSlice projection,
        @Nullable Consumer<RenderPass> setup, boolean clip, boolean depthAlways
    ) {
        GpuTextureView color = target.getColorTextureView();
        GpuTextureView depth = target.useDepth ? target.getDepthTextureView() : null;
        
        drawMeshToViews(
            label, pipeline, meshData, color, depth,
            modelView, projection, setup, clip, depthAlways,
            () -> IPGlHelper.bindWrite(target, false)
        );
    }
    
    /**
     * Draws the mesh into explicit attachments.
     * The framebuffer that the render pass binds is not the caller's one, so the caller supplies the code that rebinds it.
     */
    public static void drawMeshToViews(
        String label, RenderPipeline pipeline, MeshData meshData,
        GpuTextureView color, @Nullable GpuTextureView depth,
        Matrix4f modelView, @Nullable GpuBufferSlice projection,
        @Nullable Consumer<RenderPass> setup, boolean clip, boolean depthAlways,
        Runnable restoreFramebuffer
    ) {
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(
            modelView, new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f(), 1.0f
        );
        
        boolean oldForce = FrontClipping.forceApplyClipping;
        FrontClipping.forceApplyClipping = clip;
        PortalDraw.depthAlways = depthAlways;
        
        try (meshData) {
            GpuBuffer vertexBuffer = pipeline.getVertexFormat().uploadImmediateVertexBuffer(meshData.vertexBuffer());
            GpuBuffer indexBuffer;
            VertexFormat.IndexType indexType;
            if (meshData.indexBuffer() == null) {
                RenderSystem.AutoStorageIndexBuffer sequential =
                    RenderSystem.getSequentialBuffer(meshData.drawState().mode());
                indexBuffer = sequential.getBuffer(meshData.drawState().indexCount());
                indexType = sequential.type();
            }
            else {
                indexBuffer = pipeline.getVertexFormat().uploadImmediateIndexBuffer(meshData.indexBuffer());
                indexType = meshData.drawState().indexType();
            }
            
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Immersive Portals " + label, color, OptionalInt.empty(), depth, OptionalDouble.empty()
            )) {
                pass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transform);
                if (projection != null) {
                    pass.setUniform("Projection", projection);
                }
                if (setup != null) {
                    setup.accept(pass);
                }
                pass.setVertexBuffer(0, vertexBuffer);
                pass.setIndexBuffer(indexBuffer, indexType);
                pass.drawIndexed(0, 0, meshData.drawState().indexCount(), 1);
            }
        }
        finally {
            if (depthAlways) {
                GlStateManager._depthFunc(GL11.GL_LEQUAL);
            }
            PortalDraw.depthAlways = false;
            FrontClipping.forceApplyClipping = oldForce;
            restoreFramebuffer.run();
        }
    }
}
