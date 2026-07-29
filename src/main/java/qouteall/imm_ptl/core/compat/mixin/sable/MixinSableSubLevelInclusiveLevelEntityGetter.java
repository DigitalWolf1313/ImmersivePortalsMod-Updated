package qouteall.imm_ptl.core.compat.mixin.sable;

import dev.ryanhcode.sable.util.SubLevelInclusiveLevelEntityGetter;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.mixin.common.mc_util.IELevelEntityGetterAdapter;

@Pseudo
@Mixin(SubLevelInclusiveLevelEntityGetter.class)
@Implements(@Interface(iface = IELevelEntityGetterAdapter.class, prefix = "iPortals$"))
public abstract class MixinSableSubLevelInclusiveLevelEntityGetter {

    @Shadow
    @Final
    private LevelEntityGetter<?> delegate;

    public EntitySectionStorage<?> iPortals$getCache() {
        return this.delegate instanceof IELevelEntityGetterAdapter adapter ? adapter.getCache() : null;
    }

    public EntityLookup<?> iPortals$getIndex() {
        return this.delegate instanceof IELevelEntityGetterAdapter adapter ? adapter.getIndex() : null;
    }
}