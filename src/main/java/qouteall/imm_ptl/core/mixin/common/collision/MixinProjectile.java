package qouteall.imm_ptl.core.mixin.common.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.entity.UUIDLookup;
import net.minecraft.world.level.entity.UniquelyIdentifyable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Projectile.class)
public abstract class MixinProjectile extends MixinEntity {
    
    // Makes it recognize the owner in another dimension.
    @WrapOperation(
        method = "getOwner",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/EntityReference;get(Lnet/minecraft/world/entity/EntityReference;Lnet/minecraft/world/level/entity/UUIDLookup;Ljava/lang/Class;)Lnet/minecraft/world/level/entity/UniquelyIdentifyable;"
        )
    )
    private UniquelyIdentifyable wrapGetOwner(
        EntityReference<?> reference, UUIDLookup<?> lookup, Class<?> entityClass,
        Operation<UniquelyIdentifyable> original
    ) {
        UniquelyIdentifyable result = original.call(reference, lookup, entityClass);
        if (result != null || reference == null || !(lookup instanceof ServerLevel serverLevel)) {
            return result;
        }
        
        for (ServerLevel level : serverLevel.getServer().getAllLevels()) {
            Entity entity = level.getEntity(reference.getUUID());
            if (entity != null && entityClass.isInstance(entity)) {
                return entity;
            }
        }
        return null;
    }
    
//    @Shadow
//    public abstract void onHit(HitResult hitResult);
//
//    @Inject(method = "Lnet/minecraft/world/entity/projectile/Projectile;onHit(Lnet/minecraft/world/phys/HitResult;)V", at = @At(value = "HEAD"), cancellable = true)
//    protected void onHit(HitResult hitResult, CallbackInfo ci) {
//        Entity this_ = (Entity) (Object) this;
//        if (hitResult instanceof BlockHitResult) {
//            Block hittingBlock = this_.level().getBlockState(((BlockHitResult) hitResult).getBlockPos()).getBlock();
//            if (hitResult.getType() == HitResult.Type.BLOCK &&
//                hittingBlock == PortalPlaceholderBlock.instance
//            ) {
//                ci.cancel();
//            }
//        }
//    }
//
    
}