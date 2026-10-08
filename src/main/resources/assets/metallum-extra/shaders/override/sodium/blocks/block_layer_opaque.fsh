#version 330 core

// Terrain, drawn by Sodium. The same shader serves solid, cut-out and translucent terrain; Sodium tells them
// apart with ALPHA_CUTOUT (not set, 0.5, and 0.01).

#include "globals.glsl"
#include "color.glsl"
#include "noise.glsl"
#include "sky.glsl"
#include "shadow.glsl"
#include "lighting.glsl"
#include "fog.glsl"
#include "sodium_globals.glsl"
#include "block_types.glsl"
#include "waving.glsl"
#include "water.glsl"

uniform sampler2D u_BlockTex;

in vec4 v_Color;
in vec2 v_TexCoord;
in vec2 v_Light;
in vec3 v_Position;
in float v_Fade;
flat in uint v_Type;

out vec4 fragColor;

// The block atlas is sampled with a smoothing sampler so it can use mip levels in the distance. Up close that
// would blur each texel into its neighbours, so the lookup is pulled to the texel's middle and only allowed to
// blend across the last screen pixel before a texel edge: crisp texels with smooth edges.
vec4 mx_block_texel(vec2 uv) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texels = uv / u_TexelSize;
    vec2 per_pixel = max(sqrt(du * du + dv * dv) / u_TexelSize, vec2(1.0e-5));
    vec2 middle = floor(texels) + 0.5;
    vec2 offset = clamp((texels - middle) / per_pixel, -0.5, 0.5);
    return textureGrad(u_BlockTex, (middle + offset) * u_TexelSize, du, dv);
}

void main() {
    vec4 texel = mx_block_texel(v_TexCoord);
    vec4 color = texel * v_Color;

#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    // Sodium's mesh has no normals. Every face is flat, so the normal is the direction the surface position
    // does not change in from one pixel to the next; it is then turned to face the camera.
    vec3 normal = normalize(cross(dFdx(v_Position), dFdy(v_Position)));
    if (dot(normal, v_Position) > 0.0) {
        normal = -normal;
    }

    uint type = v_Type;
    float fog = mx_fog_amount(v_Position, u_EnvironmentFog, u_RenderFog);
    fog = max(fog, 1.0 - v_Fade);

    if (type == MX_BLOCK_WATER) {
        // The water texture's light and dark texels are kept only as a faint pattern.
        vec3 water_color = v_Color.rgb * mix(1.0, texel.r + 0.35, 0.3);
        if (MxParams2.w > 0.5) {
            fragColor = mx_water_below(water_color, v_Position, normal, v_Light, u_FogColor);
            return;
        }
        if (MxSkyHorizon.w < 0.5) {
            fragColor = mx_water(water_color, v_Position, normal, v_Light, fog);
            return;
        }
        // Under a closed sky (the Nether, blindness) water is lit like glass, below.
    }

    // Leaves and plants are thin: light comes through them, so their shaded side is not fully dark. A plant's
    // crossed quads have no meaningful facing of their own and are lit like the ground they grow from.
    float wrap = 0.0;
    float ambient_scale = 1.0;
    if (type == MX_BLOCK_LEAVES) {
        wrap = 0.3;
        ambient_scale = 0.9;
    } else if (type == MX_BLOCK_PLANT || type == MX_BLOCK_PLANT_UPPER) {
        wrap = 0.35;
        normal = vec3(0.0, 1.0, 0.0);
    }

    vec3 albedo = mx_to_linear(color.rgb);
    float facing = mx_facing(normal, wrap);
    float shadow = facing > 0.0 ? mx_shadow(v_Position, normal) : 1.0;

    // How much a surface mirrors the sun: polished things and metal a lot, most blocks a little. Rain wets
    // everything under the open sky, which darkens it and makes it shine.
    float gloss = 0.08;
    vec3 highlight = vec3(1.0);
    if (type == MX_BLOCK_SHINY) {
        gloss = 0.55;
    } else if (type == MX_BLOCK_METAL) {
        gloss = 0.8;
        highlight = albedo / max(mx_luminance(albedo), 0.05);
    } else if (type == MX_BLOCK_LEAVES || type == MX_BLOCK_PLANT || type == MX_BLOCK_PLANT_UPPER) {
        gloss = 0.0;
    }
    float wet = MxSkyAmbient.w * smoothstep(0.75, 1.0, v_Light.y) * (MxSkyHorizon.w < 0.5 ? 1.0 : 0.0);
    if (type != MX_BLOCK_LAVA) {
        gloss = max(gloss, 0.5 * wet);
        albedo *= 1.0 - 0.3 * wet;
    }

    vec3 lit = mx_lit(albedo, normal, v_Position, v_Light, facing, shadow) * ambient_scale;
    lit += mx_specular(normal, v_Position, gloss, highlight, shadow, v_Light);

    // Light comes through leaves and plants: seen against the sun they glow with it instead of going black.
    if (wrap > 0.0) {
        float against = pow(max(dot(normalize(v_Position), MxLightDir.xyz), 0.0), 6.0);
        if (against > 0.01) {
            float reached = facing > 0.0 ? shadow : mx_shadow(v_Position, normal);
            lit += albedo * MxLightColor.rgb * against * 0.45 * reached * smoothstep(0.08, 0.5, v_Light.y);
        }
    }

    // Blocks that give off light show it themselves, more the brighter they are; lava most of all.
    if (type == MX_BLOCK_LAVA) {
        lit = albedo * 4.5;
    } else if (type >= MX_BLOCK_GLOWING) {
        float level = mx_block_light_level(type);
        lit += albedo * level * level * 3.0;
    }

    if (MxFeatures.w == 2.0) {
        vec4 nearby = mx_light_color_at(v_Position);
        fragColor = vec4(nearby.rgb * nearby.a, 1.0);
        return;
    }
    fragColor = vec4(mx_fogged(lit, v_Position, fog, u_FogColor), color.a);
}
