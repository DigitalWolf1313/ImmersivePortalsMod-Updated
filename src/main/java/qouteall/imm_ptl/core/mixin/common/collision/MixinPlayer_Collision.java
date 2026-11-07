package qouteall.imm_ptl.core.mixin.common.collision;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.collision.CollisionHelper;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.ducks.IEEntity;

@SuppressWarnings("resource")
@Mixin(Player.class)
public abstract class MixinPlayer_Collision {
    
    @Inject(method = "canPlayerFitWithinBlocksAndEntitiesWhen", at = @At("HEAD"), cancellable = true)
    public void onCanPlayerFitWithinBlocksAndEntitiesWhen(Pose pose, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity this_ = (LivingEntity) (Object) this;
        
        AABB box = this_.getDimensions(pose).makeBoundingBox(this_.position());
        AABB activeCollisionBox = ((IEEntity) this_).ip_getActiveCollisionBox(box);
        if (activeCollisionBox == null) {
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(this_.level().noCollision(
            this_, activeCollisionBox.deflate(1.0E-7)
            // TODO check the issue of wrongly crouch after going through a scaling portal
            //  when head is touching ceiling because of floating point error
        ));
    }
    
    // Vanilla only checks this side's blocks and ignores the portal's collision exclusion.
    // That makes crouching on ground reachable only through a portal clamp all horizontal movement,
    // and makes the player not stop at an edge that is on the other side of the portal.
    @Inject(method = "canFallAtLeast", at = @At("RETURN"), cancellable = true)
    private void onCanFallAtLeast(double x, double z, float distance, CallbackInfoReturnable<Boolean> cir) {
        Player this_ = (Player) (Object) this;
        
        PortalCollisionHandler handler = ((IEEntity) this_).ip_getPortalCollisionHandler();
        if (handler == null || !handler.hasCollisionEntry()) {
            return;
        }
        
        AABB aabb = this_.getBoundingBox();
        AABB box = new AABB(
            aabb.minX + x, aabb.minY - distance - 1.0E-5F, aabb.minZ + z,
            aabb.maxX + x, aabb.minY, aabb.maxZ + z
        );
        
        // Entity collisions such as boats still count as support.
        boolean blocks = handler.collidesWithBlocks(this_, box);
        boolean entities = !this_.level().getEntityCollisions(this_, box).isEmpty();
        boolean supported = blocks || entities;
        
        cir.setReturnValue(!supported);
    }
}
