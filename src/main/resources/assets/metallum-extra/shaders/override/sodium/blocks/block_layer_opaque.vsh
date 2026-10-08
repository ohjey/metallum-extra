#version 330 core

// Terrain, drawn by Sodium. Replaces Sodium's own terrain shader while shaders are on.

#include "globals.glsl"
#include "sodium_globals.glsl"
#include "sodium_vertex.glsl"
#include "block_types.glsl"
#include "waving.glsl"

uniform isamplerBuffer u_SectionTimeInfo;

out vec4 v_Color;
out vec2 v_TexCoord;
out vec2 v_Light;
out vec3 v_Position;
out float v_Fade;
flat out uint v_Type;

void main() {
    // Camera-relative world space: the model-view matrix only turns it to face where the camera looks.
    vec3 position = mx_wave(mx_chunk_position(), mx_chunk_type(), mx_chunk_texture_top());

    gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);

    // A newly loaded section fades in from the fog over a short time.
    int loaded_at = texelFetch(u_SectionTimeInfo, int(u_RegionID * 256u + mx_chunk_section())).r;
    v_Fade = loaded_at < 0 ? 1.0 : clamp(float(u_CurrentTime - loaded_at) * u_FadePeriodInv, 0.0, 1.0);

    v_Color = mx_chunk_color();
    v_TexCoord = mx_chunk_texcoord(u_TexCoordShrink);
    v_Light = mx_chunk_light();
    v_Position = position;
    v_Type = mx_chunk_type();
}
