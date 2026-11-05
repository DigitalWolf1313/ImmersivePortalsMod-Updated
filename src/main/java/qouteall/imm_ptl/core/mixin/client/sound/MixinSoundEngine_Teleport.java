package qouteall.imm_ptl.core.mixin.client.sound;

import com.mojang.blaze3d.audio.Listener;
import com.mojang.blaze3d.audio.ListenerTransform;
import net.minecraft.client.Camera;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEAbstractSoundInstance;
import qouteall.imm_ptl.core.ducks.IESoundEngine;
import qouteall.imm_ptl.core.teleportation.CrossPortalSound;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

@Mixin(SoundEngine.class)
public class MixinSoundEngine_Teleport implements IESoundEngine {
    @Shadow
    @Final
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;
    
    @Shadow
    @Final
    private Map<SoundInstance, Integer> queuedSounds;
    
    @Shadow
    @Final
    private Listener listener;
    
    @Shadow
    @Final
    private SoundEngineExecutor executor;
    
    @Shadow
    private boolean loaded;
    
    @Override
    public int ip_transformSoundPositions(UnaryOperator<Vec3> transformation) {
        // A sound can be in both maps, but it must only be moved once.
        Set<SoundInstance> moved = Collections.newSetFromMap(new IdentityHashMap<>());
        
        for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : instanceToChannel.entrySet()) {
            SoundInstance sound = entry.getKey();
            if (!portal_isMovable(sound) || !moved.add(sound)) {
                continue;
            }
            
            Vec3 newPos = portal_moveSound(sound, transformation);
            
            // The sound engine only refreshes the channel position of tickable sounds.
            entry.getValue().execute(channel -> channel.setSelfPosition(newPos));
        }
        
        // A queued sound reads its position when it starts playing.
        for (SoundInstance sound : queuedSounds.keySet()) {
            if (portal_isMovable(sound) && moved.add(sound)) {
                portal_moveSound(sound, transformation);
            }
        }
        
        return moved.size();
    }
    
    @Override
    public void ip_setListenerTransform(Vec3 position, Vec3 forward, Vec3 up) {
        if (!loaded) {
            return;
        }
        
        ListenerTransform transform = new ListenerTransform(position, forward, up);
        executor.execute(() -> listener.setTransform(transform));
    }
    
    /**
     * The listener is normally updated from the camera before rendering. That camera can still
     * be at the pre-teleport position (when the player teleported while ticking), which would
     * make the whole audio scene jump for a moment. The listener was already moved together
     * with the sounds, so skip that stale update.
     */
    @Inject(method = "updateSource", at = @At("HEAD"), cancellable = true)
    private void ip_onUpdateSource(Camera camera, CallbackInfo ci) {
        if (!loaded || !camera.isInitialized()) {
            return;
        }
        
        ListenerTransform cameraTransform = new ListenerTransform(
            camera.getPosition(),
            new Vec3(camera.getLookVector()),
            new Vec3(camera.getUpVector())
        );
        
        if (CrossPortalSound.onCameraListenerUpdate(cameraTransform)) {
            ci.cancel();
        }
    }
    
    // Tickable sounds follow their own source, so they fix their position by themselves.
    private static boolean portal_isMovable(SoundInstance sound) {
        return !sound.isRelative() &&
            !(sound instanceof TickableSoundInstance) &&
            sound instanceof IEAbstractSoundInstance;
    }
    
    private static Vec3 portal_moveSound(SoundInstance sound, UnaryOperator<Vec3> transformation) {
        Vec3 newPos = transformation.apply(new Vec3(sound.getX(), sound.getY(), sound.getZ()));
        ((IEAbstractSoundInstance) sound).ip_setPos(newPos);
        return newPos;
    }
}
