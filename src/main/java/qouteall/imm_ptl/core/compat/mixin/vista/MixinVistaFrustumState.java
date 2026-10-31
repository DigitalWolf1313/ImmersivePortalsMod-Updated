package qouteall.imm_ptl.core.compat.mixin.vista;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;

@Pseudo
@Mixin(targets = "net.mehvahdjukaar.vista.client.renderer.LevelRendererFrustumState", remap = false)
public abstract class MixinVistaFrustumState {
    @Shadow public abstract void resetForLevelRendererReload();
    
    // This is weak, so the renderer of an unloaded world can be freed.
    @Unique private WeakReference<LevelRenderer> ip$lastRenderer = new WeakReference<>(null);
    
    @Inject(method = "apply", at = @At("HEAD"))
    private void ip$resetForDifferentWorld(LevelRenderer renderer, CallbackInfo ci) {
        LevelRenderer last = ip$lastRenderer.get();
        if (last != null && last != renderer) {
            resetForLevelRendererReload();
        }
        ip$lastRenderer = new WeakReference<>(renderer);
    }
}