package qouteall.imm_ptl.core.compat.mixin.sodium;

import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumCloudContext;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

import java.util.Objects;

/**
 * Optimizes Sodium's cloud rendering the same way
 * {@link qouteall.imm_ptl.core.mixin.client.render.optimization.MixinLevelRenderer_Clouds}
 * optimizes vanilla's: by caching the built cloud geometry per rendered view
 * (the main view and each portal view), instead of rebuilding
 * (retessellating) it every time the rendered view switches, e.g. every time
 * rendering switches between the main view and a same-dimension portal's
 * view. {@link CloudRenderer} only keeps a single built geometry at a time,
 * so without this, every switch between views with different cloud cell
 * positions forces a full rebuild, which can take upward of 20ms.
 */
@Mixin(value = CloudRenderer.class, remap = false)
public abstract class MixinSodiumCloudRenderer {
    
    @Shadow
    @Nullable
    private CloudRenderer.CloudGeometry builtGeometry;
    
    /**
     * {@link CloudRenderer#render}
     */
    @Redirect(
        method = "render",
        at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lnet/caffeinemc/mods/sodium/client/render/immediate/CloudRenderer;builtGeometry:Lnet/caffeinemc/mods/sodium/client/render/immediate/CloudRenderer$CloudGeometry;"
        )
    )
    private CloudRenderer.CloudGeometry ip_onReadBuiltGeometry(
        CloudRenderer instance,
        @Local(ordinal = 0) CloudRenderer.CloudGeometryParameters parameters
    ) {
        if (RenderStates.getRenderedPortalNum() == 0 || !IPGlobal.cloudOptimization) {
            return this.builtGeometry;
        }
        
        CloudRenderer.CloudGeometry current = this.builtGeometry;
        
        // the currently-built geometry already matches this view -- nothing to swap
        if (current != null && Objects.equals(current.params(), parameters)) {
            return current;
        }
        
        // the currently-built geometry (if any) belongs to a different view
        // (different cell position/orientation/render mode). Archive it
        // instead of letting Sodium overwrite or discard it, so it can be
        // reused without rebuilding next time that view is rendered
        if (current != null && current.vertexBuffer() != null) {
            SodiumCloudContext.appendContext(new SodiumCloudContext(current));
        }
        
        CloudRenderer.CloudGeometry cached = SodiumCloudContext.findAndTakeGeometry(parameters);
        
        if (cached != null) {
            this.builtGeometry = cached;
            return cached;
        }
        
        // no cached geometry available for this view. Return null (instead
        // of the archived geometry above) so that Sodium allocates a fresh
        // VertexBuffer rather than reusing (and corrupting) the one we just
        // archived
        return null;
    }
}
