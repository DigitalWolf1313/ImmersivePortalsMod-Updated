package qouteall.imm_ptl.core.mixin.common.interaction;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;
import qouteall.imm_ptl.core.portal.PortalUtils;

import java.util.ArrayList;

@Mixin(Item.class)
public class MixinItem_Interaction {
    @Redirect(
        method = "getPlayerPOVHitResult",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
        )
    )
    private static BlockHitResult onGetPlayerPOVHitResult(
        Level instance, ClipContext clipContext,
        @Local Player player
    ) {
        BlockManipulationServer.Context redirectContext =
            BlockManipulationServer.REDIRECT_CONTEXT.get();
        Level raytraceWorld = redirectContext == null ? instance : player.level();
        Vec3 delta = clipContext.getTo().subtract(clipContext.getFrom());
        Vec3 direction = delta.normalize();
        PortalUtils.PortalAwareRaytraceResult rtResult = PortalUtils.portalAwareRayTraceFull(
            raytraceWorld, clipContext.getFrom(),
            direction, delta.length(),
            player,
            ((IEClipContext) clipContext).ip_getBlock(),
            ((IEClipContext) clipContext).ip_getFluid(),
            new ArrayList<>(),
            BlockManipulationServer.MAX_PORTAL_LAYERS
        );
        
        if (rtResult != null) {
            return rtResult.hitResult();
        }
        
        return BlockHitResult.miss(
            clipContext.getTo(),
            Direction.getNearest(direction.x, direction.y, direction.z),
            BlockPos.containing(clipContext.getTo())
        );
    }
    
    @Inject(method = "getPlayerPOVHitResult", at = @At("HEAD"), cancellable = true)
    private static void onGetPlayerPOVHitResult(
        Level level, Player player, ClipContext.Fluid fluidMode,
        CallbackInfoReturnable<BlockHitResult> cir
    ) {
        BlockManipulationServer.Context context =
            BlockManipulationServer.REDIRECT_CONTEXT.get();
        if (context != null && context.blockHitResult() != null) {
            cir.setReturnValue(context.blockHitResult());
        }
    }
}
