package qouteall.imm_ptl.core.ducks;

public interface IEEntityInterpolation {
    double ip_getLerpX();
    
    double ip_getLerpY();
    
    double ip_getLerpZ();
    
    double ip_getLerpYRot();
    
    double ip_getLerpXRot();
    
    int ip_getLerpSteps();
    
    void ip_setLerpSteps(int steps);
}
