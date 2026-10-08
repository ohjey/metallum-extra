#version 330

// Particles, rain and snow. See entity.vsh.

#include "globals.glsl"
#include "game_transforms.glsl"
#include "game_lightmap.glsl"
#include "model.glsl"

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;

out vec3 mxPosition;
out vec2 mxLight;
out vec4 mxColor;

void main() {
    if (MxParams.x == MX_PHASE_SHADOW) {
        // A particle always faces the camera and has no shape the light could see; it casts no shadow.
        gl_Position = vec4(0.0, 0.0, -2.0, 1.0);
        return;
    }
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = length(Position);
    cylindricalVertexDistance = max(length(Position.xz), abs(Position.y));
    texCoord0 = UV0;
    vertexColor = Color * mx_game_light(Sampler2, UV2);

    mxPosition = mx_world_position(Position);
    mxLight = clamp(vec2(UV2) / 240.0, 0.0, 1.0);
    mxColor = Color;
}
