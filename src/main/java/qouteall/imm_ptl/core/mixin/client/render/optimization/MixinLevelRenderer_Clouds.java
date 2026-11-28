package qouteall.imm_ptl.core.mixin.client.render.optimization;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.render.IPCloudViewStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

/**
 * Avoids rebuilding the cloud mesh every time the rendered view switches.
 * Each portal view swaps its own mesh state into the renderer while it renders, see {@link IPCloudViewStates}.
 */
@Mixin(CloudRenderer.class)
public abstract class MixinLevelRenderer_Clouds {
    @Shadow
    private boolean needsRebuild;
    
    @Shadow
    private int prevCellX;
    
    @Shadow
    private int prevCellZ;
    
    @Shadow
    private CloudRenderer.RelativeCameraPos prevRelativeCameraPos;
    
    @Shadow
    @Nullable
    private CloudStatus prevType;
    
    @Shadow
    @Nullable
    private CloudRenderer.TextureData texture;
    
    @Shadow
    private int quadCount;
    
    @Shadow
    @Nullable
    private MappableRingBuffer utb;
    
    @Unique
    private IPCloudViewStates.@Nullable State ip_swappedState = null;
    
    @Inject(method = "render", at = @At("HEAD"))
    private void ip_swapInViewState(CallbackInfo ci) {
        ip_swappedState = null;
        
        if (!IPGlobal.cloudOptimization || !WorldRenderInfo.isRendering()) {
            return;
        }
        
        IPCloudViewStates.State state = IPCloudViewStates.get(WorldRenderInfo.getRenderingDescription());
        
        if (state.texture != this.texture) {
            state.texture = this.texture;
            state.needsRebuild = true;
        }
        
        ip_swap(state);
        ip_swappedState = state;
    }
    
    @Inject(method = "render", at = @At("RETURN"))
    private void ip_swapOutViewState(CallbackInfo ci) {
        if (ip_swappedState != null) {
            ip_swap(ip_swappedState);
            ip_swappedState = null;
        }
    }
    
    @Inject(method = "markForRebuild", at = @At("HEAD"))
    private void ip_onMarkForRebuild(CallbackInfo ci) {
        IPCloudViewStates.markAllForRebuild();
    }
    
    @Inject(method = "close", at = @At("HEAD"))
    private void ip_onClose(CallbackInfo ci) {
        IPCloudViewStates.cleanup();
    }
    
    @Unique
    private void ip_swap(IPCloudViewStates.State state) {
        boolean tmpNeedsRebuild = this.needsRebuild;
        this.needsRebuild = state.needsRebuild;
        state.needsRebuild = tmpNeedsRebuild;
        
        int tmpCellX = this.prevCellX;
        this.prevCellX = state.prevCellX;
        state.prevCellX = tmpCellX;
        
        int tmpCellZ = this.prevCellZ;
        this.prevCellZ = state.prevCellZ;
        state.prevCellZ = tmpCellZ;
        
        CloudRenderer.RelativeCameraPos tmpRelativePos = this.prevRelativeCameraPos;
        this.prevRelativeCameraPos = state.prevRelativeCameraPos;
        state.prevRelativeCameraPos = tmpRelativePos;
        
        CloudStatus tmpType = this.prevType;
        this.prevType = state.prevType;
        state.prevType = tmpType;
        
        int tmpQuadCount = this.quadCount;
        this.quadCount = state.quadCount;
        state.quadCount = tmpQuadCount;
        
        MappableRingBuffer tmpUtb = this.utb;
        this.utb = state.utb;
        state.utb = tmpUtb;
    }
}
