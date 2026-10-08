#version 330

// Blocks the game draws one at a time instead of as part of a chunk: falling sand, blocks pushed by pistons,
// blocks shown by block entities. See entity.vsh.

#include "globals.glsl"
#include "game_transforms.glsl"
#include "game_lightmap.glsl"
#include "model.glsl"

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

out vec3 mxPosition;
out vec2 mxLight;
out vec4 mxColor;

void main() {
    vec3 position = Position + ModelOffset;
    mxPosition = mx_world_position(position);
    mxLight = clamp(vec2(UV2) / 240.0, 0.0, 1.0);
    mxColor = Color;

    if (MxParams.x == MX_PHASE_SHADOW) {
        gl_Position = mx_shadow_clip(mxPosition);
    } else {
        gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    }

    sphericalVertexDistance = length(position);
    cylindricalVertexDistance = max(length(position.xz), abs(position.y));
    vertexColor = Color * mx_game_light(Sampler2, UV2);
    texCoord0 = UV0;
}
