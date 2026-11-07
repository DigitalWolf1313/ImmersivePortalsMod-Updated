package qouteall.imm_ptl.core.mixin.common.collision;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.ducks.IEEntity;

@Mixin(Player.class)
public abstract class MixinPlayer_Collision {
    
    @Redirect(
        method = "maybeBackOffFromEdge",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"
        )
    )
    private boolean ip_checkPortalCollisionSupport(Level level, Entity entity, AABB box) {
        PortalCollisionHandler handler = ((IEEntity) entity).ip_getPortalCollisionHandler();
        if (handler == null || !handler.hasCollisionEntry()) {
            return level.noCollision(entity, box);
        }
        
        boolean blocks = handler.collidesWithBlocks(entity, box);
        boolean entities = !level.getEntityCollisions(entity, box).isEmpty();
        return !blocks && !entities;
    }
}
