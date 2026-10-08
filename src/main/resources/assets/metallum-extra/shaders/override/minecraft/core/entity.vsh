#version 330

// Mobs, players, armor, block entities and most other models. Replaces the game's entity shader while shaders
// are on; outside the world (menus, the inventory, the held item) it gives the game's own result.

#include "globals.glsl"
#include "game_transforms.glsl"
#include "game_lightmap.glsl"
#include "model.glsl"
#if defined(PER_FACE_LIGHTING) || !defined(NO_CARDINAL_LIGHTING)
#include "game_lighting.glsl"
#endif

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

#ifndef NO_OVERLAY
uniform sampler2D Sampler1;
#endif

#ifndef EMISSIVE
uniform sampler2D Sampler2;
#endif

out float sphericalVertexDistance;
out float cylindricalVertexDistance;

#ifdef PER_FACE_LIGHTING
out vec4 vertexPerFaceColorBack;
out vec4 vertexPerFaceColorFront;
#else
out vec4 vertexColor;
#endif

#ifndef EMISSIVE
out vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
out vec4 overlayColor;
#endif

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

#ifdef PER_FACE_LIGHTING
    float first = dot(Light0_Direction, Normal);
    float second = dot(Light1_Direction, Normal);
    vertexPerFaceColorFront = vec4(Color.rgb * mx_game_shade(first, second), Color.a);
    vertexPerFaceColorBack = vec4(Color.rgb * mx_game_shade(-first, -second), Color.a);
#elif defined(NO_CARDINAL_LIGHTING)
    vertexColor = Color;
#else
    vertexColor = vec4(Color.rgb * mx_game_shade(dot(Light0_Direction, Normal), dot(Light1_Direction, Normal)), Color.a);
#endif

#ifndef EMISSIVE
    lightMapColor = mx_game_light(Sampler2, UV2);
#endif

#ifndef NO_OVERLAY
    overlayColor = texelFetch(Sampler1, UV1, 0);
#endif

    texCoord0 = UV0;
#ifdef APPLY_TEXTURE_MATRIX
    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#endif
}
