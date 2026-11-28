#version 150

uniform sampler2D DiffuseSampler;

out vec4 fragColor;

void main() {
    // The sampled framebuffer has the same size as the one being drawn into.
    vec2 size = vec2(textureSize(DiffuseSampler, 0));

    fragColor = vec4(texture(DiffuseSampler, gl_FragCoord.xy / size).xyz, 1.0);
}
