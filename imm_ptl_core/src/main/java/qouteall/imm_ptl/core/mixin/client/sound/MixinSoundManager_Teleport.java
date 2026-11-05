package qouteall.imm_ptl.core.mixin.client.sound;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.ducks.IESoundEngine;
import qouteall.imm_ptl.core.ducks.IESoundManager;

@Mixin(SoundManager.class)
public class MixinSoundManager_Teleport implements IESoundManager {
    @Shadow
    @Final
    private SoundEngine soundEngine;

    @Override
    public IESoundEngine ip_getSoundEngine() {
        return (IESoundEngine) soundEngine;
    }
}
