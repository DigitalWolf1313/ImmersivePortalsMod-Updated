package qouteall.imm_ptl.core.compat.iris_compatibility;

import com.mojang.blaze3d.platform.GlStateManager;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.texture.PixelType;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.DepthTexture;
import net.irisshaders.iris.targets.RenderTarget;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL43C;
import qouteall.q_misc_util.Helper;

import java.util.ArrayDeque;

/**
 * Keeps a small pool of copies of Iris's shadow map, including its depth and color buffers.
 * The outer world's shadow map is copied into a {@link Storage} before a portal world overwrites it.
 * It is copied back after the portal world is rendered.
 */
public class ShadowMapSwapper {
    public static class Storage {
        public final DepthTexture depthTexture;
        public final DepthTexture noTranslucents;
        private final int resolution;
        private final ShadowMapSwapper shadowMapSwapper;
        
        // Backup textures of the shadowcolor buffers. A zero means that it is not created yet.
        private final int[] colorTextures;
        
        // Whether the shadowcolor buffer existed during the latest copy from Iris.
        private final boolean[] colorBackedUp;
        
        public Storage(int resolution, ShadowMapSwapper shadowMapSwapper) {
            depthTexture = new DepthTexture(
                "ip_shadowtex0_backup", resolution, resolution, DepthBufferFormat.DEPTH
            );
            noTranslucents = new DepthTexture(
                "ip_shadowtex1_backup", resolution, resolution, DepthBufferFormat.DEPTH
            );
            this.resolution = resolution;
            this.shadowMapSwapper = shadowMapSwapper;
            
            int colorBufferNum = shadowMapSwapper.shadowRenderTargets.getRenderTargetCount();
            colorTextures = new int[colorBufferNum];
            colorBackedUp = new boolean[colorBufferNum];
        }
        
        void dispose() {
            depthTexture.destroy();
            noTranslucents.destroy();
            
            for (int i = 0; i < colorTextures.length; i++) {
                if (colorTextures[i] != 0) {
                    GlStateManager._deleteTexture(colorTextures[i]);
                    colorTextures[i] = 0;
                }
                colorBackedUp[i] = false;
            }
        }
        
        private int createColorTexture(InternalTextureFormat format) {
            int[] textures = new int[1];
            GlStateManager._genTextures(textures);
            int texture = textures[0];
            
            IrisRenderSystem.texImage2D(
                texture, GL11C.GL_TEXTURE_2D, 0,
                format.getGlFormat(), resolution, resolution, 0,
                format.getPixelFormat().getGlFormat(), PixelType.UNSIGNED_BYTE.getGlFormat(), null
            );
            IrisRenderSystem.texParameteri(
                texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST
            );
            
            return texture;
        }
        
        private void copyTexture(int sourceTexture, int destinationTexture) {
            GL43C.glCopyImageSubData(
                sourceTexture,
                GL43C.GL_TEXTURE_2D,
                0, 0, 0, 0,
                destinationTexture,
                GL43C.GL_TEXTURE_2D,
                0, 0, 0, 0,
                resolution, resolution,
                1
            );
        }
        
        void copyFromIrisShadowRenderTargets() {
            if (shadowMapSwapper.disposed) {
                return;
            }
            
            ShadowRenderTargets shadowRenderTargets = shadowMapSwapper.shadowRenderTargets;
            
            copyTexture(shadowRenderTargets.getDepthTexture().getTextureId(), depthTexture.getTextureId());
            copyTexture(
                shadowRenderTargets.getDepthTextureNoTranslucents().getTextureId(),
                noTranslucents.getTextureId()
            );
            
            // Iris creates the shadowcolor buffers lazily, so some of them may not exist.
            for (int i = 0; i < colorTextures.length; i++) {
                RenderTarget target = shadowRenderTargets.get(i);
                colorBackedUp[i] = target != null;
                
                if (target == null) {
                    continue;
                }
                
                if (colorTextures[i] == 0) {
                    colorTextures[i] = createColorTexture(target.getInternalFormat());
                }
                
                copyTexture(shadowRenderTargets.getColorTextureId(i), colorTextures[i]);
            }
        }
        
        void copyToIrisShadowRenderTargets() {
            if (shadowMapSwapper.disposed) {
                return;
            }
            
            ShadowRenderTargets shadowRenderTargets = shadowMapSwapper.shadowRenderTargets;
            
            copyTexture(depthTexture.getTextureId(), shadowRenderTargets.getDepthTexture().getTextureId());
            copyTexture(
                noTranslucents.getTextureId(),
                shadowRenderTargets.getDepthTextureNoTranslucents().getTextureId()
            );
            
            for (int i = 0; i < colorTextures.length; i++) {
                if (colorBackedUp[i]) {
                    copyTexture(colorTextures[i], shadowRenderTargets.getColorTextureId(i));
                }
            }
        }
        
        public void restitute() {
            shadowMapSwapper.restituteStorage(this);
        }
    }
    
    private static final int storageNumLimit = 3;
    
    private final int shadowMapResolution;
    private final ShadowRenderTargets shadowRenderTargets;
    private int createdNum = 0;
    private final ArrayDeque<Storage> queue = new ArrayDeque<>();
    private boolean disposed = false;
    
    public ShadowMapSwapper(int resolution, ShadowRenderTargets shadowRenderTargets) {
        this.shadowMapResolution = resolution;
        this.shadowRenderTargets = shadowRenderTargets;
    }
    
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        
        if (queue.size() != createdNum) {
            // Storages that are still acquired are destroyed when they are restituted.
            Helper.LOGGER.warn(
                "[iPortal] Shadow Map Swapper disposed while {} of {} storages are still in use",
                createdNum - queue.size(), createdNum
            );
        }
        
        for (Storage storage : queue) {
            storage.dispose();
        }
        queue.clear();
    }
    
    @Nullable
    public Storage acquireStorage() {
        if (disposed) {
            return null;
        }
        
        if (queue.isEmpty()) {
            if (createdNum < storageNumLimit) {
                createdNum++;
                return new Storage(shadowMapResolution, this);
            }
            else {
                return null;
            }
        }
        return queue.pollFirst();
    }
    
    void restituteStorage(@Nullable Storage storage) {
        if (storage == null) {
            return;
        }
        
        if (disposed) {
            storage.dispose();
            return;
        }
        
        queue.addFirst(storage);
    }
}
