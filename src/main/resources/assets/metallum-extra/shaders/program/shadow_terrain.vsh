#version 330 core

// Terrain as the sun or moon sees it, for the shadow map.

#include "globals.glsl"
#include "shadow_bend.glsl"
#include "sodium_vertex.glsl"
#include "block_types.glsl"
#include "waving.glsl"

out vec2 v_TexCoord;

void main() {
    vec3 position = mx_wave(mx_chunk_position(), mx_chunk_type(), mx_chunk_texture_top());
    vec4 clip = MxShadowMat * vec4(position, 1.0);
    clip.xy = mx_shadow_bend(clip.xy);
    // Anything nearer the light than the map reaches is flattened onto its near side instead of being cut off:
    // it still has to cast its shadow.
    clip.z = min(clip.z, 0.9999);
    gl_Position = clip;
    v_TexCoord = mx_chunk_texcoord(vec2(0.0));
}
