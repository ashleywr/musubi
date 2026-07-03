#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 relPos;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // Vertices are baked camera-relative in world axes (the camera rotation lives
    // in ModelViewMat), so the fragment shader can do its optics in world space
    // with the camera at the origin.
    relPos = Position;
    vertexColor = Color;
}
