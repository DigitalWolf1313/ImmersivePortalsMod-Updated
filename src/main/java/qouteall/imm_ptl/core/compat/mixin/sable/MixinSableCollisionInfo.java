package qouteall.imm_ptl.core.compat.mixin.sable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = Entity.class, priority = 1200)
public abstract class MixinSableCollisionInfo {
    @Shadow
    private void setOnGroundWithMovement(boolean onGround, Vec3 collided) {}

    @WrapOperation(
        method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;setOnGroundWithMovement(ZLnet/minecraft/world/phys/Vec3;)V"
        )
    )
    private void iPortals$FixSableNullCollisionInfo(
        Entity instance, boolean onGround, Vec3 collided, Operation<Void> original
    ) {
        if (instance instanceof EntityMovementExtension ext
            && ext.sable$getCollisionInfo() == null) {
            // When Sable's collision info is null (e.g., entity in a dimension without Sable sublevels),
            // call the original method directly to bypass Sable's wrapper which would crash.
            setOnGroundWithMovement(onGround, collided);
            return;
        }
        original.call(instance, onGround, collided);
    }
}
