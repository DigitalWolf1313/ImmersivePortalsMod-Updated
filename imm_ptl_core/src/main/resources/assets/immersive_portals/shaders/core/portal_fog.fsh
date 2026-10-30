#version 150

uniform float FogStart;
uniform float FogEnd;
uniform int FogShape;
uniform vec4 FogColor;

in vec3 cameraRelativePosition;
out vec4 fragColor;

void main() {
    // Evaluate per fragment so large portal triangles do not fog uniformly.
    float distance = FogShape == 0
        ? length(cameraRelativePosition)
        : max(length(cameraRelativePosition.xz), abs(cameraRelativePosition.y));
    float amount = distance <= FogStart ? 0.0
        : distance >= FogEnd ? 1.0 : smoothstep(FogStart, FogEnd, distance);
    fragColor = vec4(FogColor.rgb, amount * FogColor.a);
}
