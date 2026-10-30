#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 cameraRelativePosition;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Keep world axes for cylindrical fog, including when looking up or down.
    cameraRelativePosition = Position;
}
