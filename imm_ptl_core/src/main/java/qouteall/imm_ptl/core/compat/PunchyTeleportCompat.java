package qouteall.imm_ptl.core.compat;

/** Bridge populated by optional Punchy mixins when its physics systems are used. */
public final class PunchyTeleportCompat {
    public interface Resettable {
        void ip$resetAfterTeleport();
    }
    
    public static Resettable movement;
    public static Resettable pendulum;
    
    private PunchyTeleportCompat() {}
    
    public static void onTeleport() {
        if (movement != null) {
            movement.ip$resetAfterTeleport();
        }
        if (pendulum != null) pendulum.ip$resetAfterTeleport();
    }
}
