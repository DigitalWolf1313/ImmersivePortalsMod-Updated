package qouteall.imm_ptl.core.compat.sodium_compatibility;

import com.mojang.blaze3d.vertex.VertexBuffer;
import me.jellysquid.mods.sodium.client.render.immediate.CloudRenderer;
import net.minecraft.client.CloudStatus;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.q_misc_util.Helper;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Caches a built Sodium {@link CloudRenderer} cloud mesh (the geometry that
 * lives in {@link CloudRenderer}'s {@code vertexBuffer}) so that it can be
 * reused across different rendered views (the main view and portal views)
 * instead of being rebuilt (retessellated) every time the rendered view
 * switches, which is expensive (a rebuild can take upward of 20ms).
 * <p>
 * This plays the same role for Sodium's cloud renderer as
 * {@link qouteall.imm_ptl.core.render.context_management.CloudContext} plays
 * for vanilla's cloud renderer. Unlike vanilla clouds though, Sodium's built
 * geometry does not bake the per-dimension cloud color into the vertices
 * (that color is applied separately, as a shader uniform, at draw time), so
 * the cached geometry only needs to be keyed by the parameters that Sodium
 * itself uses to decide when to rebuild
 * ({@link CloudRenderer.CloudGeometryParameters}: the cloud cell position,
 * the render distance and the cloud render mode), without needing a dimension
 * or color key.
 *
 * @see qouteall.imm_ptl.core.compat.mixin.sodium.MixinSodiumCloudRenderer
 */
public class SodiumCloudContext {
    
    public VertexBuffer vertexBuffer;
    public CloudGeometryParameters parameters;
    
    public static final ArrayList<SodiumCloudContext> contexts = new ArrayList<>();
    
    public static void init() {
        IPGlobal.clientCleanupSignal.connect(SodiumCloudContext::cleanup);
        ClientWorldLoader.clientDimensionDynamicRemoveSignal.connect(dim -> cleanup());
    }
    
    public SodiumCloudContext(VertexBuffer vertexBuffer, CloudGeometryParameters parameters) {
        this.vertexBuffer = vertexBuffer;
        this.parameters = parameters;
    }
    
    private static void cleanup() {
        for (SodiumCloudContext context : contexts) {
            context.dispose();
        }
        contexts.clear();
    }
    
    public void dispose() {
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
    
    /**
     * Finds a cached geometry whose build parameters match, removing it from
     * the pool (the caller takes ownership of it). Returns null if none is
     * cached, in which case the caller should rebuild.
     */
    @Nullable
    public static SodiumCloudContext findAndTakeGeometry(CloudGeometryParameters parameters) {
        int i = Helper.indexOf(contexts, c ->
            Objects.equals(c.parameters, parameters)
        );
        
        if (i == -1) {
            return null;
        }
        
        return contexts.remove(i);
    }
    
    public static void appendContext(SodiumCloudContext context) {
        // avoid keeping stale duplicate entries for the same build parameters
        // (shouldn't normally happen, but avoids leaking a GL buffer if it does)
        contexts.removeIf(c -> {
            if (Objects.equals(c.parameters, context.parameters)) {
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
    
    /**
     * The parameters Sodium uses to decide whether a cloud geometry needs to
     * be rebuilt. Two geometries with equal parameters are interchangeable,
     * so this doubles as the cache lookup key.
     */
    public record CloudGeometryParameters(
        int centerCellX, int centerCellZ, int renderDistance, CloudStatus cloudStatus
    ) {
    }
}
