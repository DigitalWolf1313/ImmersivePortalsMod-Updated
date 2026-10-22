package qouteall.imm_ptl.core.compat.mixin.vanillabackport;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.IPVanillaBackportCompat;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * Makes Vanilla Backport spear targeting portal-aware, including the client's crosshair pick.
 * Hooks both method descriptors so the mixin also works outside development environments.
 */
@Pseudo
@Mixin(targets = "com.blackgear.vanillabackport.core.util.ProjectileUtils", remap = false)
public class MixinVanillaBackportProjectileUtils {
    @Inject(
        method = {
            "getHitEntitiesAlong(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ljava/util/function/Predicate;Lnet/minecraft/world/phys/Vec3;FLnet/minecraft/world/level/ClipContext$Block;)Lcom/mojang/datafixers/util/Either;",
            "getHitEntitiesAlong(Lnet/minecraft/class_1297;Lnet/minecraft/class_243;Lnet/minecraft/class_243;Ljava/util/function/Predicate;Lnet/minecraft/class_243;FLnet/minecraft/class_3959$class_3960;)Lcom/mojang/datafixers/util/Either;"
        },
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 1
    )
    private static void iPortals$portalAwareHitEntities(
        Entity source, Vec3 origin, Vec3 from, Predicate<Entity> matching,
        Vec3 to, float entityMargin, ClipContext.Block clipType,
        CallbackInfoReturnable<Either<BlockHitResult, Collection<EntityHitResult>>> cir
    ) {
        Collection<EntityHitResult> hits = IPVanillaBackportCompat.portalAwareHitEntities(
            source, origin, from, matching, to, entityMargin, clipType
        );
        
        if (hits == null) {
            // no portal on the way. vanilla backport's own logic
            return;
        }
        
        if (!hits.isEmpty()) {
            cir.setReturnValue(Either.right(hits));
        }
        else {
            Vec3 dir = to.subtract(origin);
            cir.setReturnValue(Either.left(BlockHitResult.miss(
                to, Direction.getNearest(dir.x, dir.y, dir.z), BlockPos.containing(to)
            )));
        }
    }
}
