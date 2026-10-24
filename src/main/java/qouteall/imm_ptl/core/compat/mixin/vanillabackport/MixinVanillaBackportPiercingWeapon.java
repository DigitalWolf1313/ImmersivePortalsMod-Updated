package qouteall.imm_ptl.core.compat.mixin.vanillabackport;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import qouteall.imm_ptl.core.compat.IPVanillaBackportCompat;

@Pseudo
@Mixin(
    targets = "com.blackgear.vanillabackport.common.level.components.PiercingWeapon",
    remap = false
)
public class MixinVanillaBackportPiercingWeapon {
    @WrapOperation(
        method = "attack",
        at = @At(
            value = "INVOKE",
            target = "Lcom/blackgear/vanillabackport/common/api/extensions/entity/spear/MobSpearHandler;vb$stabAttack(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/Entity;FZZZ)Z",
            remap = false
        ),
        require = 0,
        remap = false
    )
    private boolean iPortals$relocateAttackerForPortalHitNamed(
        @Coerce Object handler, EquipmentSlot equipmentSlot, Entity target, float baseDamage,
        boolean dealsDamage, boolean dealsKnockback, boolean dismounts,
        Operation<Boolean> original
    ) {
        return iPortals$relocateAttackerForPortalHit(
            handler, equipmentSlot, target, baseDamage,
            dealsDamage, dealsKnockback, dismounts, original
        );
    }
    
    @WrapOperation(
        method = "attack",
        at = @At(
            value = "INVOKE",
            target = "Lcom/blackgear/vanillabackport/common/api/extensions/entity/spear/MobSpearHandler;vb$stabAttack(Lnet/minecraft/class_1304;Lnet/minecraft/class_1297;FZZZ)Z",
            remap = false
        ),
        require = 0,
        remap = false
    )
    private boolean iPortals$relocateAttackerForPortalHitIntermediary(
        @Coerce Object handler, EquipmentSlot equipmentSlot, Entity target, float baseDamage,
        boolean dealsDamage, boolean dealsKnockback, boolean dismounts,
        Operation<Boolean> original
    ) {
        return iPortals$relocateAttackerForPortalHit(
            handler, equipmentSlot, target, baseDamage,
            dealsDamage, dealsKnockback, dismounts, original
        );
    }
    
    private boolean iPortals$relocateAttackerForPortalHit(
        Object handler, EquipmentSlot equipmentSlot, Entity target, float baseDamage,
        boolean dealsDamage, boolean dealsKnockback, boolean dismounts,
        Operation<Boolean> original
    ) {
        Entity attacker = (Entity) handler;
        return IPVanillaBackportCompat.withAttackerRelocatedForHit(
            attacker, target,
            () -> original.call(
                handler, equipmentSlot, target, baseDamage,
                dealsDamage, dealsKnockback, dismounts
            )
        );
    }
}
