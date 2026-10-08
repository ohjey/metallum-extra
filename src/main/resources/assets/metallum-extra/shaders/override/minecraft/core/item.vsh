#version 330

// Items lying in the world, in item frames and in hands. See entity.vsh.

#include "globals.glsl"
#include "game_transforms.glsl"
#include "game_lightmap.glsl"
#include "game_lighting.glsl"
#include "model.glsl"

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;

out vec3 mxPosition;
out vec3 mxNormal;
out vec2 mxLight;
out vec4 mxColor;

void main() {
    mxPosition = mx_world_position(Position);
    mxNormal = mx_world_normal(Normal);
    mxLight = clamp(vec2(UV2) / 240.0, 0.0, 1.0);
    mxColor = Color;

    if (MxParams.x == MX_PHASE_SHADOW) {
        gl_Position = mx_shadow_clip(mxPosition);
    } else {
        gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    }

    sphericalVertexDistance = length(Position);
    cylindricalVertexDistance = max(length(Position.xz), abs(Position.y));

    vertexColor = vec4(Color.rgb * mx_game_shade(dot(Light0_Direction, Normal), dot(Light1_Direction, Normal)), Color.a);
    lightMapColor = mx_game_light(Sampler2, UV2);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;
}
