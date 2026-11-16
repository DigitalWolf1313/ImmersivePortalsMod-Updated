package qouteall.imm_ptl.core.compat.sodium_compatibility;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkStatus;
import net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteUtil;
import net.caffeinemc.mods.sodium.client.world.LevelRendererExtension;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.compat.mixin.sodium.IESodiumWorldRenderer;
import qouteall.imm_ptl.core.render.FrustumCuller;

@Environment(EnvType.CLIENT)
public class SodiumInterface {
    
    @Nullable
    public static FrustumCuller frustumCuller = null;
    
    /**
     * Whether the latest Sodium visible section search was done for a portal view.
     * Vanilla collects visible entities before Sodium updates the terrain in a frame,
     * so entity culling then reads the section visibility left by the previous frame's last portal view.
     */
    public static boolean lastSectionSearchWasForPortal = false;
    
    public static class Invoker {
        public boolean isSodiumPresent() {
            return false;
        }
        
        public Object createNewContext(int renderDistance) {
            return null;
        }
        
        public void switchContextWithCurrentWorldRenderer(Object context) {
        
        }
        
        public int getVisibleSectionCount() {
            return 0;
        }
        
        public void markSpriteActive(TextureAtlasSprite sprite) {
        
        }
        
        public void onClientChunkLoaded(ClientLevel world, int chunkX, int chunkZ) {
        
        }
        
        public void onClientChunkUnloaded(ClientLevel world, int chunkX, int chunkZ) {
        
        }
    }
    
    public static Invoker invoker = new Invoker();
    
    public static class OnSodiumPresent extends Invoker {
        @Override
        public boolean isSodiumPresent() {
            return true;
        }
        
        @Override
        public Object createNewContext(int renderDistance) {
            return new SodiumRenderingContext(renderDistance);
        }
        
        @Override
        public void switchContextWithCurrentWorldRenderer(Object context) {
            SodiumWorldRenderer swr =
                ((LevelRendererExtension) Minecraft.getInstance().levelRenderer).sodium$getWorldRenderer();
            swr.scheduleTerrainUpdate();
            
            RenderSectionManager renderSectionManager =
                ((IESodiumWorldRenderer) swr).ip_getRenderSectionManager();
            
            ((IESodiumRenderSectionManager) renderSectionManager)
                .ip_swapContext(((SodiumRenderingContext) context));
            
            swr.scheduleTerrainUpdate();
        }
        
        @Override
        public int getVisibleSectionCount() {
            SodiumWorldRenderer swr =
                ((LevelRendererExtension) Minecraft.getInstance().levelRenderer).sodium$getWorldRenderer();
            return swr.getVisibleChunkCount();
        }
        
        @Override
        public void markSpriteActive(TextureAtlasSprite sprite) {
            SpriteUtil.markSpriteActive(sprite);
        }
        
        @Override
        public void onClientChunkLoaded(ClientLevel world, int chunkX, int chunkZ) {
            ChunkTrackerHolder.get(world)
                .onChunkStatusAdded(chunkX, chunkZ, ChunkStatus.FLAG_HAS_BLOCK_DATA);
        }
        
        @Override
        public void onClientChunkUnloaded(ClientLevel world, int chunkX, int chunkZ) {
            ChunkTrackerHolder.get(world)
                .onChunkStatusRemoved(chunkX, chunkZ, ChunkStatus.FLAG_HAS_BLOCK_DATA);
        }
    }
    
}
