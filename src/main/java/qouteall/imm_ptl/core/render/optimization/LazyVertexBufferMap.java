package qouteall.imm_ptl.core.render.optimization;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.RenderType;

import java.util.HashMap;

/**
 * Replacement for RenderSection's eagerly-filled buffer map. VertexBuffers (GL objects) are only
 * created on the render thread when first needed. Worker threads must never create them, so
 * {@link #ensureAll()} is called on the render thread before any compile/resort task is created.
 */
public class LazyVertexBufferMap extends HashMap<RenderType, VertexBuffer> {
    
    @Override
    public VertexBuffer get(Object key) {
        VertexBuffer buffer = super.get(key);
        if (buffer == null && key instanceof RenderType renderType
            && RenderSystem.isOnRenderThread()
            && RenderType.chunkBufferLayers().contains(renderType)
        ) {
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            super.put(renderType, buffer);
        }
        return buffer;
    }
    
    public void ensureAll() {
        if (size() >= RenderType.chunkBufferLayers().size()) {
            return;
        }
        for (RenderType renderType : RenderType.chunkBufferLayers()) {
            get(renderType);
        }
    }
}
