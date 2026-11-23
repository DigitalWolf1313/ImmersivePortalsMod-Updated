package qouteall.imm_ptl.core.ducks;

/**
 * Records the client tick in which a particle last ticked.
 * A particle that crosses a portal while ticking would otherwise tick again in the destination world during the same tick.
 */
public interface IEParticleTickStamp {
    int portal_getLastTickStamp();
    
    void portal_setLastTickStamp(int stamp);
}
