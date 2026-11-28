package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import qouteall.imm_ptl.core.IPCGlobal;


/**
 * Vanilla depth textures have no stencil bits.
 * A render target with stencil enabled gets its depth texture reallocated as a depth-stencil texture.
 * The framebuffer attachment is switched to depth-stencil by the DirectStateAccess mixins.
 */
public class StencilTargetHelper {
    
    /**
     * Reallocates the texture storage as depth-stencil.
     * The texture keeps its GL id, so existing views stay valid.
     */
    public static void convertToDepthStencil(GpuTexture depthTexture) {
        if (!(depthTexture instanceof GlTexture glTexture)) {
            return;
        }
        
        boolean separated = IPCGlobal.useSeparatedStencilFormat;
        
        int oldBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTexture.glId());
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D, 0,
            separated ? GL30.GL_DEPTH32F_STENCIL8 : GL30.GL_DEPTH24_STENCIL8,
            glTexture.getWidth(0), glTexture.getHeight(0), 0,
            GL30.GL_DEPTH_STENCIL,
            separated ? GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV : GL30.GL_UNSIGNED_INT_24_8,
            0L
        );
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldBinding);
    }
    
    /**
     * Queries the GL storage format because GL ids get reused after a texture is deleted.
     */
    public static boolean isDepthStencilTexture(int textureId) {
        if (textureId == 0) {
            return false;
        }
        
        int oldBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        int format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, oldBinding);
        
        return format == GL30.GL_DEPTH24_STENCIL8 || format == GL30.GL_DEPTH32F_STENCIL8;
    }
}
