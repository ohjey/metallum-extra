#version 330

// Sun rays (color) and ambient occlusion (alpha), at half size, from the finished world's depth.

#include "globals.glsl"
#include "noise.glsl"
#include "shadow_bend.glsl"

uniform sampler2D MxSceneDepth;
uniform sampler2D MxShadowMap;

in vec2 texCoord;
out vec4 fragColor;

vec3 view_position(vec2 uv, float depth) {
    vec4 view = MxProjInv * vec4(uv * 2.0 - 1.0, depth, 1.0);
    return view.xyz / view.w;
}

// Depth is read one texel at a time, and a point is always rebuilt from the middle of the texel its depth came
// from. Rebuilt from anywhere else in the texel it lands off the surface it belongs to; on flat ground or water
// seen at a shallow angle that counted as something in the way, and showed as stripes of shade.
float depth_at(ivec2 texel) {
    return texelFetch(MxSceneDepth, clamp(texel, ivec2(0), textureSize(MxSceneDepth, 0) - 1), 0).r;
}

vec3 position_at(ivec2 texel) {
    return view_position((vec2(texel) + 0.5) * MxScreen.zw, max(depth_at(texel), 1.0e-5));
}

// Whether the sun reaches a point of the air, from the shadow map. Outside the map everything counts as lit.
float lit_at(vec3 position) {
    vec4 clip = MxShadowMat * vec4(position, 1.0);
    vec2 uv = mx_shadow_bend(clip.xy) * 0.5 + 0.5;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        return 1.0;
    }
    return step(textureLod(MxShadowMap, uv, 0.0).r, clip.z + 0.0005);
}

// Light scattered towards the eye by the air along the line of sight, where the sun reaches it. Haze scatters
// mostly forwards, so the rays are brightest looking towards the sun and faint looking away from it.
vec3 sun_rays(vec3 direction, float distance_to) {
    // The shadow map only knows the terrain that has been built around the player, and under ground that is
    // little more than the cave itself: the rock overhead is missing from it and the cave's air counts as sunlit.
    // So the rays follow the sky light where the camera is, and are gone where there is none.
    float under_sky = smoothstep(0.05, 0.6, MxParams3.x);
    if (MxFeatures.x < 0.5 || MxLightDir.w <= 0.0 || under_sky <= 0.0) {
        return vec3(0.0);
    }
    float range = MxParams2.x;
    float end = min(distance_to, range);
    const int STEPS = 14;
    float stride = end / float(STEPS);
    float jitter = mx_dither(gl_FragCoord.xy);
    float lit = 0.0;
    for (int i = 0; i < STEPS; i++) {
        lit += lit_at(direction * ((float(i) + jitter) * stride));
    }
    // The sunlit share of the whole stretch the shadow map covers, 0..1. A share, not a length: a longer shadow
    // distance must not thicken the air.
    lit *= stride / range;

    // 1 looking straight at the sun, about a third 30 degrees off, next to nothing sideways.
    float cosine = dot(direction, MxLightDir.xyz);
    const float G = 0.6;
    float forward = pow((1.0 - G) * (1.0 - G) / (1.0 + G * G - 2.0 * G * cosine), 1.5);
    // Thin air in the open at midday, so the rays show where something breaks them and do not veil the view;
    // thicker when the sun is low, and in rain.
    float air = 0.04 + 0.10 * (1.0 - smoothstep(0.1, 0.5, MxLightDir.y)) + 0.10 * MxSkyAmbient.w;
    // Moonlight makes faint rays, and against a dark sky even a little reads as a grey veil; keep it slight.
    air *= mix(0.25, 1.0, MxLightColor.w);
    return MxLightColor.rgb * MxLightDir.w * lit * air * forward * under_sky;
}

// The surface normal at a texel, from the depth of its neighbours. Of the two neighbours on each axis the
// nearer in depth is used, so the normal stays right at the edge of an object instead of spanning the gap.
vec3 surface_normal(ivec2 texel, vec3 position) {
    vec3 right = position_at(texel + ivec2(1, 0));
    vec3 left = position_at(texel - ivec2(1, 0));
    vec3 up = position_at(texel + ivec2(0, 1));
    vec3 down = position_at(texel - ivec2(0, 1));
    vec3 across = abs(right.z - position.z) < abs(position.z - left.z) ? right - position : position - left;
    vec3 along = abs(up.z - position.z) < abs(position.z - down.z) ? up - position : position - down;
    vec3 normal = normalize(cross(across, along));
    return dot(normal, position) > 0.0 ? -normal : normal;
}

// How much of the surrounding hemisphere is blocked by nearby surfaces: 1 open, lower in corners.
float occlusion(ivec2 texel, vec2 uv, vec3 position) {
    // Corners far away are smaller than a pixel, and what is far is under fog, which shade must not darken.
    float near = 1.0 - smoothstep(48.0, 112.0, -position.z);
    if (MxFeatures.y < 0.5 || near <= 0.0) {
        return 1.0;
    }
    vec3 normal = surface_normal(texel, position);
    const float RADIUS = 0.9;
    // The sampling disc covers RADIUS blocks around the point, but never more than a small part of the screen:
    // for ground right under the camera it would otherwise span half the picture and gather up the player and
    // every blade of grass, which showed as a dark band across the near ground.
    vec2 reach = vec2(MxProj[0][0], MxProj[1][1]) * 0.5 * RADIUS / max(-position.z, 0.1);
    reach = min(reach, vec2(0.05));
    // What depth can tell of a point's place gets coarser with distance; a rise smaller than that is no rise.
    float slack = 0.02 - 0.003 * position.z;
    float turn = mx_dither(gl_FragCoord.xy) * 6.2831853;
    float blocked = 0.0;
    float counted = 0.0;
    const int TAPS = 12;
    for (int i = 0; i < TAPS; i++) {
        float angle = turn + float(i) * 2.3999632;
        float spread = sqrt((float(i) + 0.5) / float(TAPS));
        vec2 at = uv + vec2(cos(angle), sin(angle)) * spread * reach;
        if (at.x < 0.0 || at.x > 1.0 || at.y < 0.0 || at.y > 1.0) {
            continue;
        }
        counted += 1.0;
        ivec2 tap = ivec2(at * MxScreen.xy);
        if (tap == texel || depth_at(tap) <= 0.0) {
            continue;
        }
        vec3 towards = position_at(tap) - position;
        float distance_to = length(towards);
        // Only what is really near the point in 3D can shade it; something in front of it on screen but far
        // from it in the world (the player, seen in third person) must not cast a halo onto it.
        float within = 1.0 - smoothstep(RADIUS * 0.5, RADIUS, distance_to);
        float rise = max(dot(normal, towards) - slack, 0.0);
        blocked += max(rise / max(distance_to, 1.0e-4) - 0.12, 0.0) * within;
    }
    return 1.0 - near * clamp(blocked * 3.2 / max(counted, 1.0), 0.0, 0.7);
}

void main() {
    // This image is half size: each of its pixels stands for the first of the four depth texels under it.
    ivec2 texel = ivec2(gl_FragCoord.xy) * 2;
    vec2 uv = (vec2(texel) + 0.5) * MxScreen.zw;
    float depth = depth_at(texel);
    vec3 position = view_position(uv, max(depth, 1.0e-5));
    vec3 direction = normalize(mat3(MxViewInv) * position);
    float distance_to = depth > 0.0 ? length(position) : 1.0e5;

    vec3 rays = sun_rays(direction, distance_to);
    float open = depth > 0.0 ? occlusion(texel, uv, position) : 1.0;
    fragColor = vec4(rays, open);
}
