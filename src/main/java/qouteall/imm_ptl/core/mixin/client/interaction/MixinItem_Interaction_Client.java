package qouteall.imm_ptl.core.mixin.client.interaction;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;

@Mixin(Item.class)
public class MixinItem_Interaction_Client {
    @Inject(method = "getPlayerPOVHitResult", at = @At("HEAD"), cancellable = true)
    private static void onGetPlayerPOVHitResult(
        Level level, Player player, ClipContext.Fluid fluidMode,
        CallbackInfoReturnable<BlockHitResult> cir
    ) {
        if (level.isClientSide()) {
            HitResult remoteHitResult = BlockManipulationClient.remoteHitResult;
            if (remoteHitResult instanceof BlockHitResult blockHitResult) {
                cir.setReturnValue(blockHitResult);
            }
        }
    }
}
