package qouteall.imm_ptl.core.compat.mixin.vista;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ClientWorldLoader;

@Pseudo
@Mixin(targets = "net.mehvahdjukaar.vista.VistaModClient", remap = false)
public abstract class MixinVistaClientWorldLookup {
    @Inject(method = "getLocalLevelByDimension", at = @At("RETURN"), cancellable = true)
    private static void ip$findLoadedWorld(ResourceKey<Level> dimension, CallbackInfoReturnable<Level> cir) {
        if (cir.getReturnValue() == null && ClientWorldLoader.getIsInitialized()) {
            for (ClientLevel world : ClientWorldLoader.getClientWorlds()) {
                if (world.dimension().equals(dimension)) {
                    cir.setReturnValue(world);
                    return;
                }
            }
        }
    }
}
