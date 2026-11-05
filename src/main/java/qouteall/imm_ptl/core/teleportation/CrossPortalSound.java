package qouteall.imm_ptl.core.teleportation;

import com.mojang.blaze3d.audio.ListenerTransform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IESoundEngine;
import qouteall.imm_ptl.core.ducks.IESoundManager;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

import java.util.Comparator;

@Environment(EnvType.CLIENT)
public class CrossPortalSound {
    public static final float VOLUME_RADIUS_MULT = 16f;
    public static final float MIN_SOUND_RADIUS = 16f;
    
    /**
     * The listener transform that was last submitted to the sound engine.
     * It is tracked here because the listener is updated asynchronously on the sound thread.
     */
    private static ListenerTransform audioListenerTransform = ListenerTransform.INITIAL;
    
    /**
     * After teleporting, the next camera update can still contain the pre-teleport position.
     * The listener is moved immediately instead, so that stale update must not overwrite it.
     */
    private static boolean skipNextCameraListenerUpdate = false;
    
    /**
     * Moves the playing and queued sounds through the portal together with the player.
     * Sounds are positioned in the coordinates of the dimension the player was in,
     * so without this they would end up far from the listener after the teleportation.
     * <p>
     * The listener is moved as well, otherwise it would stay in the old coordinate system
     * for a frame and the sounds would briefly cut out.
     */
    public static void onPlayerTeleported(Portal portal) {
        IESoundManager soundManager = (IESoundManager) Minecraft.getInstance().getSoundManager();
        IESoundEngine soundEngine = soundManager.ip_getSoundEngine();
        
        soundEngine.ip_transformSoundPositions(portal::transformPoint);
        
        audioListenerTransform = transformListenerTransform(audioListenerTransform, portal);
        soundEngine.ip_setListenerTransform(
            audioListenerTransform.position(),
            audioListenerTransform.forward(),
            audioListenerTransform.up()
        );
        
        skipNextCameraListenerUpdate = true;
    }
    
    /**
     * Called before the sound engine applies a camera transform to the listener.
     *
     * @return true if this camera update should be skipped because it still points to
     * the listener position from before the teleportation.
     */
    public static boolean onCameraListenerUpdate(ListenerTransform cameraTransform) {
        if (skipNextCameraListenerUpdate) {
            skipNextCameraListenerUpdate = false;
            return true;
        }
        
        audioListenerTransform = cameraTransform;
        return false;
    }
    
    /**
     * A world change discards the pending camera update,
     * so the skip flag must not leak into the next world.
     */
    public static void onClientCleanup() {
        skipNextCameraListenerUpdate = false;
    }
    
    private static ListenerTransform transformListenerTransform(
        ListenerTransform transform, Portal portal
    ) {
        return new ListenerTransform(
            portal.transformPoint(transform.position()),
            normalizeDirection(
                portal.transformLocalVecNonScale(transform.forward()),
                transform.forward()
            ),
            normalizeDirection(
                portal.transformLocalVecNonScale(transform.up()),
                transform.up()
            )
        );
    }
    
    private static Vec3 normalizeDirection(Vec3 direction, Vec3 fallback) {
        double lengthSqr = direction.lengthSqr();
        if (lengthSqr < 1.0E-8) {
            return fallback;
        }
        return direction.scale(1.0 / Math.sqrt(lengthSqr));
    }
    
    public static boolean isPlayerWorld(ClientLevel world) {
        return world.dimension() == RenderStates.originalPlayerDimension;
    }
    
    @Nullable
    public static SimpleSoundInstance createCrossPortalSound(
        ClientLevel soundWorld,
        SoundEvent soundEvent,
        SoundSource soundSource,
        Vec3 soundPos,
        float soundVol,
        float soundPitch,
        long seed
    ) {
        Minecraft client = Minecraft.getInstance();
        
        if (client.player == null) {
            return null;
        }
        
        soundWorld.getProfiler().push("cross_portal_sound");
        
        double soundRadius = Math.min(64, Math.max(VOLUME_RADIUS_MULT * soundVol, MIN_SOUND_RADIUS));
        Vec3 playerCameraPos = RenderStates.originalPlayerPos.add(
            McHelper.getEyeOffset(client.player)
        );
        
        // find portals in range of the sound
        SimpleSoundInstance result = IPMcHelper.getNearbyPortalList(
            soundWorld, soundPos, IPGlobal.maxNormalPortalRadius,
            portal -> portal.getDestDim() == RenderStates.originalPlayerDimension &&
                isPlayerInRange(portal, soundPos, soundRadius, playerCameraPos)
        ).stream().min(
            // use portal that is closest to the sound
            Comparator.comparingDouble(portal -> getPortalDistance(portal, soundPos))
        ).map(
            portal -> {
                // set sound position to the point the sound would exit the portal
                Vec3 soundEnterPortalPoint = portal.getNearestPointInPortal(soundPos);
                Vec3 soundExitPortalPoint = portal.transformPoint(soundEnterPortalPoint);
                
                // reduce volume based on distance from the sound source to the portal entry point
                float volumeToEnterPortal =
                    (float) soundEnterPortalPoint.distanceTo(soundPos) / VOLUME_RADIUS_MULT;
                
                float volumeMultiplier =
                    Math.max(0, 1 - (float) (playerCameraPos.distanceTo(soundExitPortalPoint) / VOLUME_RADIUS_MULT));
                
                float volumeAtPortal = Math.max(0, soundVol - volumeToEnterPortal) * volumeMultiplier;
                
                return new SimpleSoundInstance(
                    soundEvent,
                    soundSource,
                    volumeAtPortal,
                    soundPitch,
                    RandomSource.create(seed),
                    soundExitPortalPoint.x(),
                    soundExitPortalPoint.y(),
                    soundExitPortalPoint.z()
                );
            }
        ).orElse(null);
        
        soundWorld.getProfiler().pop();
        
        return result;
    }
    
    private static boolean isPlayerInRange(
        Portal portal, Vec3 soundPos, double soundRadius, Vec3 playerCameraPos
    ) {
        Vec3 soundExitPoint = portal.transformPoint(portal.getNearestPointInPortal(soundPos));
        return soundExitPoint.closerThan(playerCameraPos, soundRadius);
    }
    
    private static double getPortalDistance(Portal portal, Vec3 soundPos) {
        Vec3 soundEnterPortalPoint = portal.getNearestPointInPortal(soundPos);
        return soundEnterPortalPoint.distanceToSqr(soundPos);
    }
}
