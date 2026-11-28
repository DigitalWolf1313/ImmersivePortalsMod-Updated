package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.Uniform;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.my_util.SignalBiArged;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.lwjgl.opengl.GL11.GL_BACK;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_FRONT;
import static org.lwjgl.opengl.GL11.GL_RED;
import static org.lwjgl.opengl.GL11.glCullFace;
import static org.lwjgl.opengl.GL11.glReadPixels;

@SuppressWarnings("resource")
public class MyRenderHelper {
    
    public static final Minecraft client = Minecraft.getInstance();
    
    
//    public static final SignalBiArged<ResourceProvider, Consumer<ShaderInstance>> loadShaderSignal =
//        new SignalBiArged<>();
    
    public static void init() {
        
//        loadShaderSignal.connect((resourceManager, resultConsumer) -> {
//            try {
//                DrawFbInAreaShader shader = new DrawFbInAreaShader(
//                    getResourceFactory(resourceManager),
//                    "portal_draw_fb_in_area",
//                    DefaultVertexFormat.POSITION_COLOR
//                );
//                resultConsumer.accept(shader);
//                drawFbInAreaShader = shader;
//            }
//            catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        });
        
//        loadShaderSignal.connect((resourceManager, resultConsumer) -> {
//            try {
//                ShaderInstance shader = new ShaderInstance(
//                    getResourceFactory(resourceManager),
//                    "portal_area",
//                    DefaultVertexFormat.POSITION_COLOR
//                );
//                resultConsumer.accept(shader);
//                portalAreaShader = shader;
//            }
//            catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        });
//
//        loadShaderSignal.connect((resourceManager, resultConsumer) -> {
//            try {
//                ShaderInstance shader = new ShaderInstance(
//                    getResourceFactory(resourceManager),
//                    "blit_screen_noblend",
//                    DefaultVertexFormat.POSITION_TEX_COLOR
//                );
//                resultConsumer.accept(shader);
//                blitScreenNoBlendShader = shader;
//            }
//            catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        });
    }
    
//    // vanilla hardcodes the shader namespace to be "minecraft"
//    private static ResourceProvider getResourceFactory(ResourceProvider resourceManager) {
//        ResourceProvider resourceFactory = new ResourceProvider() {
//            @Override
//            public Optional<Resource> getResource(ResourceLocation resourceLocation) {
//                ResourceLocation corrected = McHelper.newResourceLocation(
//                    "immersive_portals", resourceLocation.getPath());
//                return resourceManager.getResource(corrected);
//            }
//        };
//        return resourceFactory;
//    }
    
//    public static class DrawFbInAreaShader extends ShaderInstance {
//
//        public final Uniform uniformW;
//        public final Uniform uniformH;
//
//        public DrawFbInAreaShader(
//            ResourceProvider factory, String name, VertexFormat format
//        ) throws IOException {
//            super(factory, name, format);
//
//            uniformW = getUniform("w");
//            uniformH = getUniform("h");
//        }
//
//        void loadWidthHeight(int w, int h) {
//            uniformW.set((float) w);
//            uniformH.set((float) h);
//        }
//    }
//
//    public static DrawFbInAreaShader drawFbInAreaShader;
//    public static ShaderInstance portalAreaShader;
//    public static ShaderInstance blitScreenNoBlendShader;
//
    /**
     * Draws the framebuffer's color into the screen region covered by the portal view area.
     * The framebuffer must have the same size as the main render target.
     * Both faces are drawn, as the visibility of the portal has already been decided by an earlier test.
     */
    public static void drawPortalAreaWithFramebuffer(
        Portal portal,
        RenderTarget textureProvider,
        Matrix4f modelViewMatrix,
        Matrix4f projectionMatrix
    ) {
        MeshData mesh = ViewAreaRenderer.buildViewAreaMesh(Vec3.ZERO, portal, CHelper.getCurrentCameraPos());
        if (mesh == null) {
            return;
        }
        
        PortalDraw.drawMesh(
            "framebuffer in portal area",
            PortalPipelines.drawFbInArea(false),
            mesh, client.getMainRenderTarget(),
            modelViewMatrix, IPRenderSystem.uploadProjectionMatrix(projectionMatrix),
            pass -> pass.bindSampler("DiffuseSampler", textureProvider.getColorTextureView()),
            false, false
        );
    }
    
    public static void renderScreenTriangle(Vec3 color) {
        renderScreenTriangle(
            true, false, false,
            (int) (color.x * 255),
            (int) (color.y * 255),
            (int) (color.z * 255),
            255
        );
    }
    
    public static void renderScreenTriangle(boolean writeColor, boolean writeDepth, boolean depthTest) {
        renderScreenTriangle(writeColor, writeDepth, depthTest, 255, 255, 255, 255);
    }
    
    /**
     * Fills the whole main render target with a triangle in clip space.
     * The depth range, the stencil state and the cull face mode come from the raw GL state.
     */
    public static void renderScreenTriangle(
        boolean writeColor, boolean writeDepth, boolean depthTest,
        int r, int g, int b, int a
    ) {
        PortalDraw.drawMesh(
            "screen triangle",
            PortalPipelines.screenTriangle(writeColor, writeDepth, depthTest),
            buildScreenTriangleMesh(r, g, b, a), client.getMainRenderTarget(),
            new Matrix4f(), IPRenderSystem.uploadProjectionMatrix(new Matrix4f()),
            null, false, false
        );
    }
    
    private static MeshData buildScreenTriangleMesh(int r, int g, int b, int a) {
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tessellator
            .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        
        bufferBuilder.addVertex(1, -1, 0).setColor(r, g, b, a);
        bufferBuilder.addVertex(1, 1, 0).setColor(r, g, b, a);
        bufferBuilder.addVertex(-1, 1, 0).setColor(r, g, b, a);
        
        bufferBuilder.addVertex(-1, 1, 0).setColor(r, g, b, a);
        bufferBuilder.addVertex(-1, -1, 0).setColor(r, g, b, a);
        bufferBuilder.addVertex(1, -1, 0).setColor(r, g, b, a);
        
        return bufferBuilder.buildOrThrow();
    }
    
    /**
     * {@link RenderTarget#blitToScreen(int, int)}
     */
    public static void drawScreenFrameBuffer(
        RenderTarget textureProvider,
        boolean doUseAlphaBlend,
        boolean doEnableModifyAlpha
    ) {
        int x = 0;
        int y = 0;
        
        int viewportWidth = textureProvider.viewWidth;
        int viewportHeight = textureProvider.viewHeight;
        
        drawFramebufferWithCoordinatesAndDimensions(
            textureProvider, doUseAlphaBlend, doEnableModifyAlpha,
            x, y, viewportWidth, viewportHeight
        );
    }

    public static void drawFramebuffer(
            RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha,
            float xMin, float xMax, float yMin, float yMax
    ) {
        drawFramebufferWithCoordinatesAndDimensions(
                textureProvider,
                doUseAlphaBlend, doEnableModifyAlpha,
                0, 0,
                client.getWindow().getWidth(),
                client.getWindow().getHeight()
        );
    }

    public static void drawFramebufferWithViewport(
            RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha,
            float left, float right, float bottom, float up,
            int viewportWidth, int viewportHeight
    ) {
        drawFramebufferWithCoordinatesAndDimensions(
                textureProvider,
                doUseAlphaBlend, doEnableModifyAlpha,
                0, 0,
                viewportWidth, viewportHeight
        );
    }
    
    public static void drawFramebufferWithBounds(
        RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha,
        int xMin, int xMax, int yMin, int yMax
    ) {

        drawFramebufferWithCoordinatesAndDimensions(
            textureProvider,
            doUseAlphaBlend, doEnableModifyAlpha,
            xMin, yMin,
            Mth.abs(xMax - xMin),
            Mth.abs(yMax - yMin)
        );
    }
    
    /**
     * {@link RenderTarget#blitToScreen(int, int)}
     */
    @SuppressWarnings("resource")
    @IPVanillaCopy
    public static void drawFramebufferWithCoordinatesAndDimensions(
        RenderTarget textureProvider, boolean doUseAlphaBlend, boolean doEnableModifyAlpha,
        int x, int y, int viewportWidth, int viewportHeight
    ) {
        drawFramebufferWithCoordinatesAndDimensions(
            textureProvider, client.getMainRenderTarget(), doUseAlphaBlend, doEnableModifyAlpha,
            x, y, viewportWidth, viewportHeight
        );
    }
    
    /**
     * Draws the whole source framebuffer into the destination framebuffer.
     * The raw GL stencil state is applied to the destination's stencil attachment.
     */
    public static void drawScreenFrameBufferTo(
        RenderTarget source, RenderTarget destination,
        boolean doUseAlphaBlend, boolean doEnableModifyAlpha
    ) {
        drawFramebufferWithCoordinatesAndDimensions(
            source, destination, doUseAlphaBlend, doEnableModifyAlpha,
            0, 0, source.viewWidth, source.viewHeight
        );
    }
    
    @SuppressWarnings("resource")
    @IPVanillaCopy
    public static void drawFramebufferWithCoordinatesAndDimensions(
        RenderTarget textureProvider, RenderTarget destination,
        boolean doUseAlphaBlend, boolean doEnableModifyAlpha,
        int x, int y, int viewportWidth, int viewportHeight
    ) {
        CHelper.checkGlError();
        
        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        bufferBuilder.addVertex(0.0f, 0.0f, 0.0f);
        bufferBuilder.addVertex(1.0f, 0.0f, 0.0f);
        bufferBuilder.addVertex(1.0f, 1.0f, 0.0f);
        bufferBuilder.addVertex(0.0f, 1.0f, 0.0f);
        
        int viewportY = textureProvider.viewHeight - viewportHeight - y;
        
        // When alpha blending is on, the framebuffer is treated as premultiplied.
        // The framebuffer should be initialized with zero color and zero alpha.
        // Then the result equals rendering the translucent content directly onto the target:
        // color = fbColor * 1 + dstColor * (1 - contentAlpha)
        PortalDraw.drawMesh(
            "blit framebuffer",
            PortalPipelines.blit(doUseAlphaBlend, doEnableModifyAlpha),
            bufferBuilder.buildOrThrow(), destination,
            new Matrix4f(), null,
            pass -> {
                pass.bindSampler("InSampler", textureProvider.getColorTextureView());
                GlStateManager._viewport(x, viewportY, viewportWidth, viewportHeight);
            },
            false, false
        );
        
        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);
        
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(770, 771, 1, 0);
        
        CHelper.checkGlError();
    }
    
    // it will remove the light sections that are marked to be removed
    // if not, light data will cause minor memory leak
    // and wrongly remove the light data when the chunks get reloaded to client
    // this should not run before world rendering or the smooth lighting may become abnormal in section edge
    public static void lateUpdateLight() {
        if (!ClientWorldLoader.getIsInitialized()) {
            return;
        }
        
        ClientWorldLoader.getClientWorlds().forEach(world -> {
            if (!RenderStates.isDimensionRendered(world.dimension())) {
                world.getChunkSource().getLightEngine().runLightUpdates();
            }
        });
    }
    
    /**
     * If we don't do this
     * the future created in {@link SectionRenderDispatcher#uploadSectionLayer}
     * may never complete
     */
    public static void earlyRemoteUpload() {
        if (!ClientWorldLoader.getIsInitialized()) {
            return;
        }
        
        ClientWorldLoader.WORLD_RENDERER_MAP.forEach((dim, worldRenderer) -> {
            Validate.notNull(client.level, "client.level is null");
            if (client.level.dimension() != dim) {
                worldRenderer.getSectionRenderDispatcher().uploadAllPendingUploads();
            }
        });
    }
    
    public static void applyMirrorFaceCulling() {
        glCullFace(GL_FRONT);
    }
    
    public static void recoverFaceCulling() {
        glCullFace(GL_BACK);
    }
    
    public static void clearAlphaTo1(RenderTarget mcFrameBuffer) {
        IPGlHelper.bindWrite(mcFrameBuffer, true);
        GlStateManager._colorMask(false, false, false, true);
        GL11.glClearColor(0, 0, 0, 1.0f);
        GlStateManager._clear(GL_COLOR_BUFFER_BIT);
        GlStateManager._colorMask(true, true, true, true);
    }
    
    public static void restoreViewPort() {
        Minecraft client = Minecraft.getInstance();
        GlStateManager._viewport(
            0,
            0,
            client.getMainRenderTarget().viewWidth,
            client.getMainRenderTarget().viewHeight
        );
    }
    
    public static float transformFogDistance(float value) {
        if (!WorldRenderInfo.isFogEnabled()) {
            return value * 23333;
        }
        
        // just disable fog for fuse-view portals for now
        if (PortalRendering.isRendering()) {
            Portal renderingPortal = PortalRendering.getRenderingPortal();
            
            if (renderingPortal.isFuseView()) {
                return value * 23333;
            }
        }
        
        // as non-fuse-view portals does not apply scale transformation to modelview,
        // there is no need to transform fog distance (both with and without sodium)
        
        return value;
    }
    
    private static boolean debugEnabled = false;
    
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public static void debugFramebufferDepth() {
        if (!debugEnabled) {
            return;
        }
        debugEnabled = false;
        
        int width = client.getMainRenderTarget().width;
        int height = client.getMainRenderTarget().height;
        
        
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
        
        FloatBuffer floatBuffer = directBuffer.asFloatBuffer();
        
        glReadPixels(
            0, 0, width, height,
            GL_DEPTH_COMPONENT, GL_FLOAT, floatBuffer
        );
        
        float[] data = new float[width * height];
        
        floatBuffer.rewind();
        floatBuffer.get(data);
        
        float maxValue = (float) IntStream.range(0, data.length)
            .mapToDouble(i -> data[i]).max().getAsDouble();
        float minValue = (float) IntStream.range(0, data.length)
            .mapToDouble(i -> data[i]).min().getAsDouble();
        
        byte[] grayData = new byte[width * height];
        for (int i = 0; i < data.length; i++) {
            float datum = data[i];
            
            datum = (datum - minValue) / (maxValue - minValue);
            
            grayData[i] = (byte) (datum * 255);
        }
        
        BufferedImage bufferedImage =
            new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        
        bufferedImage.setData(
            Raster.createRaster(
                bufferedImage.getSampleModel(),
                new DataBufferByte(grayData, grayData.length), new Point()
            )
        );
        
        System.out.println("oops");
    }
    
    public static void debugFramebufferColorRed() {
        if (!debugEnabled) {
            return;
        }
        debugEnabled = false;
        
        int width = client.getMainRenderTarget().width;
        int height = client.getMainRenderTarget().height;
        
        
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
        
        FloatBuffer floatBuffer = directBuffer.asFloatBuffer();
        
        glReadPixels(
            0, 0, width, height,
            GL_RED, GL_FLOAT, floatBuffer
        );
        
        float[] data = new float[width * height];
        
        floatBuffer.rewind();
        floatBuffer.get(data);
        
        float maxValue = (float) IntStream.range(0, data.length)
            .mapToDouble(i -> data[i]).max().getAsDouble();
        float minValue = (float) IntStream.range(0, data.length)
            .mapToDouble(i -> data[i]).min().getAsDouble();
        
        byte[] grayData = new byte[width * height];
        for (int i = 0; i < data.length; i++) {
            float datum = data[i];
            
            datum = (datum - minValue) / (maxValue - minValue);
            
            grayData[i] = (byte) (datum * 255);
        }
        
        BufferedImage bufferedImage =
            new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        
        bufferedImage.setData(
            Raster.createRaster(
                bufferedImage.getSampleModel(),
                new DataBufferByte(grayData, grayData.length), new Point()
            )
        );
        
        System.out.println("oops");
    }
}
