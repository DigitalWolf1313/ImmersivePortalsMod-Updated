package qouteall.imm_ptl.core.compat.sodium_compatibility;

import net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.q_misc_util.Helper;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Caches a built Sodium {@link CloudRenderer.CloudGeometry} so that it can be
 * reused across different rendered views (the main view and portal views)
 * instead of being rebuilt (retessellated) every time the rendered view
 * switches, which is expensive
 * ({@link CloudRenderer#rebuildGeometry} can take upward of 20ms).
 * <p>
 * This plays the same role for Sodium's cloud renderer as
 * {@link qouteall.imm_ptl.core.render.context_management.CloudContext} plays
 * for vanilla's cloud renderer. Unlike vanilla clouds though, Sodium's built
 * geometry does not bake the per-dimension cloud color into the vertices
 * (that color is applied separately, as a shader uniform, at draw time), so
 * the cached geometry only needs to be keyed by
 * {@link CloudRenderer.CloudGeometryParameters}, without needing a dimension
 * or color key.
 *
 * @see qouteall.imm_ptl.core.compat.mixin.sodium.MixinSodiumCloudRenderer
 */
public class SodiumCloudContext {
    
    public CloudRenderer.CloudGeometry geometry;
    
    public static final ArrayList<SodiumCloudContext> contexts = new ArrayList<>();
    
    public static void init() {
        IPCGlobal.CLIENT_CLEANUP_EVENT.register(SodiumCloudContext::cleanup);
        ClientWorldLoader.CLIENT_DIMENSION_DYNAMIC_REMOVE_EVENT.register(dim -> cleanup());
    }
    
    public SodiumCloudContext(CloudRenderer.CloudGeometry geometry) {
        this.geometry = geometry;
    }
    
    private static void cleanup() {
        for (SodiumCloudContext context : contexts) {
            context.dispose();
        }
        contexts.clear();
    }
    
    public void dispose() {
        if (geometry != null) {
            var vertexBuffer = geometry.vertexBuffer();
            if (vertexBuffer != null) {
                vertexBuffer.close();
            }
            geometry = null;
        }
    }
    
    /**
     * Finds a cached geometry whose build parameters match, removing it from
     * the pool (the caller takes ownership of it). Returns null if none is
     * cached, in which case the caller should rebuild.
     */
    @Nullable
    public static CloudRenderer.CloudGeometry findAndTakeGeometry(
        CloudRenderer.CloudGeometryParameters parameters
    ) {
        int i = Helper.indexOf(contexts, c ->
            Objects.equals(c.geometry.params(), parameters)
        );
        
        if (i == -1) {
            return null;
        }
        
        SodiumCloudContext result = contexts.remove(i);
        
        return result.geometry;
    }
    
    public static void appendContext(SodiumCloudContext context) {
        // avoid keeping stale duplicate entries for the same build parameters
        // (shouldn't normally happen, but avoids leaking a GL buffer if it does)
        contexts.removeIf(c -> {
            if (Objects.equals(c.geometry.params(), context.geometry.params())) {
                c.dispose();
                return true;
            }
            return false;
        });
        
        contexts.add(context);
        
        if (contexts.size() > 15) {
            contexts.remove(0).dispose();
        }
    }
}
