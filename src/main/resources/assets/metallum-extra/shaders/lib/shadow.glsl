// Reading the shadow map. Needs globals.glsl.

uniform sampler2D MxShadowMap;

#include "shadow_bend.glsl"
#include "noise.glsl"

// One smoothed lookup: the four texels around `uv` are each compared with `depth`, and the four yes-or-no
// answers are blended by how close `uv` is to each. Blending the answers, not the depths, is what makes a shadow
// edge slide smoothly across the map's texels as the sun turns, where a plain lookup would make it flicker from
// one texel to the next.
float mx_shadow_tap(vec2 uv, float depth) {
    ivec2 size = textureSize(MxShadowMap, 0);
    vec2 position = uv * vec2(size) - 0.5;
    ivec2 corner = ivec2(floor(position));
    vec2 blend = position - vec2(corner);
    ivec2 last = size - 1;
    // Depth here runs from 1 (nearest the light) to 0, so anything nearer the light has a larger value.
    float a = step(texelFetch(MxShadowMap, clamp(corner, ivec2(0), last), 0).r, depth);
    float b = step(texelFetch(MxShadowMap, clamp(corner + ivec2(1, 0), ivec2(0), last), 0).r, depth);
    float c = step(texelFetch(MxShadowMap, clamp(corner + ivec2(0, 1), ivec2(0), last), 0).r, depth);
    float d = step(texelFetch(MxShadowMap, clamp(corner + ivec2(1, 1), ivec2(0), last), 0).r, depth);
    return mix(mix(a, b, blend.x), mix(c, d, blend.x), blend.y);
}

// How much of the sun's or moon's light reaches a point: 1 fully lit, 0 fully shadowed.
// `position` is camera-relative world space; `normal` is the surface normal there.
float mx_shadow(vec3 position, vec3 normal) {
    float strength = MxLightDir.w;
    if (strength <= 0.0) return 1.0;

    float range = MxParams2.x;
    float distance_from_camera = length(position);
    float beyond = smoothstep(range * 0.80, range * 0.97, distance_from_camera);
    if (beyond >= 1.0) return 1.0;
    float facing = clamp(dot(normal, MxLightDir.xyz), 0.0, 1.0);

    // Look the point up a little way off the surface, or the surface shadows itself in stripes. A texel covers
    // more ground further from the player and when the light grazes the surface, so step further off there.
    float squeeze = (distance_from_camera / range) * MX_SHADOW_BEND + (1.0 - MX_SHADOW_BEND);
    float texel_size = 2.0 * range * MxParams.w * squeeze;
    vec3 lifted = position + normal * texel_size * (1.5 + 2.5 * (1.0 - facing));

    vec4 clip = MxShadowMat * vec4(lifted, 1.0);
    vec2 uv = mx_shadow_bend(clip.xy) * 0.5 + 0.5;
    float depth = clip.z + 0.00035;

    // Four smoothed lookups in a square give a soft edge about three texels wide, with no grain.
    float reach = MxParams.w * 0.75;
    float lit = mx_shadow_tap(uv + vec2(-reach, -reach), depth)
              + mx_shadow_tap(uv + vec2(reach, -reach), depth)
              + mx_shadow_tap(uv + vec2(-reach, reach), depth)
              + mx_shadow_tap(uv + vec2(reach, reach), depth);
    lit *= 0.25;

    return mix(1.0, mix(lit, 1.0, beyond), strength);
}
