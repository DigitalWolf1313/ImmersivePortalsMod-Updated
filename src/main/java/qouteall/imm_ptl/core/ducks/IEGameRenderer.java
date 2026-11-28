package qouteall.imm_ptl.core.ducks;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.fog.FogRenderer;

public interface IEGameRenderer {
    void ip_setLightmapTextureManager(LightTexture manager);
    
    boolean ip_getDoRenderHand();
    
    void ip_setDoRenderHand(boolean cond);
    
    FogRenderer ip_getFogRenderer();
    
    void ip_setCamera(Camera camera);
    
    void ip_setIsRenderingPanorama(boolean cond);
}
