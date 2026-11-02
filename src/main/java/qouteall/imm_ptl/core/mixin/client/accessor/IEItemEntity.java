package qouteall.imm_ptl.core.mixin.client.accessor;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemEntity.class)
public interface IEItemEntity {
    @Accessor("age")
    void ip_setAge(int age);
    
    @Mutable
    @Accessor("bobOffs")
    void ip_setBobOffs(float bobOffs);
}
