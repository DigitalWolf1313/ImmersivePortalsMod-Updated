package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.Minecraft;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.q_misc_util.Helper;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps one framebuffer per requested size, as views of different sizes can be rendered in one frame.
 * Old sizes are evicted when the cache is used again, and all sizes are destroyed on client cleanup.
 */
public class SecondaryFrameBuffer {
    // The number of frames after which an unused size is destroyed.
    private static final int MAX_IDLE_FRAMES = 600;
    
    // Limits the cached sizes in case the requested sizes keep changing.
    private static final int MAX_ENTRIES = 24;
    
    private static class Entry {
        final TextureTarget target;
        int lastUsedFrame;
        
        Entry(TextureTarget target, int lastUsedFrame) {
            this.target = target;
            this.lastUsedFrame = lastUsedFrame;
        }
    }
    
    public TextureTarget fb;
    
    // The eldest entry is the least recently used one, as the map is access-ordered.
    private final Map<Long, Entry> buffers = new LinkedHashMap<>(8, 0.75f, true);
    
    public void prepare() {
        RenderTarget mainFrameBuffer = Minecraft.getInstance().getMainRenderTarget();
        int width = mainFrameBuffer.viewWidth;
        int height = mainFrameBuffer.viewHeight;
        prepare(width, height);
    }
    
    public void prepare(int width, int height) {
        int frame = RenderStates.frameIndex;
        long size = ((long) width << 32) | (height & 0xffffffffL);
        
        Entry entry = buffers.get(size);
        if (entry == null) {
            TextureTarget target = new TextureTarget(
                width, height,
                true,//has depth attachment
                Minecraft.ON_OSX
            );
            target.checkStatus();
            entry = new Entry(target, frame);
            buffers.put(size, entry);
            Helper.LOGGER.info("Secondary Framebuffer init {}x{} ({} cached)", width, height, buffers.size());
        }
        entry.lastUsedFrame = frame;
        fb = entry.target;
        
        evictUnused(frame);
    }
    
    /**
     * Destroys sizes that have been idle for too long.
     * Also destroys the least recently used sizes when there are too many.
     */
    private void evictUnused(int frame) {
        Iterator<Entry> iterator = buffers.values().iterator();
        while (iterator.hasNext() && buffers.size() > 1) {
            Entry eldest = iterator.next();
            boolean idle = frame - eldest.lastUsedFrame > MAX_IDLE_FRAMES;
            if (!idle && buffers.size() <= MAX_ENTRIES) {
                // The remaining entries were used more recently.
                break;
            }
            iterator.remove();
            eldest.target.destroyBuffers();
        }
    }
    
    public void destroy() {
        buffers.values().forEach(entry -> entry.target.destroyBuffers());
        buffers.clear();
        fb = null;
    }
    
    
}
