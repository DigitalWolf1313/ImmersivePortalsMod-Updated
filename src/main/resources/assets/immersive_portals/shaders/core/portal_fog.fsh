#version 150

#moj_import <minecraft:fog.glsl>

in vec3 cameraRelativePosition;

out vec4 fragColor;

void main() {
    // Evaluate per fragment so large portal triangles do not fog uniformly.
    float amount = total_fog_value(
        fog_spherical_distance(cameraRelativePosition),
        fog_cylindrical_distance(cameraRelativePosition),
        FogEnvironmentalStart, FogEnvironmentalEnd,
        FogRenderDistanceStart, FogRenderDistanceEnd
    );
    fragColor = vec4(FogColor.rgb, amount * FogColor.a);
}
