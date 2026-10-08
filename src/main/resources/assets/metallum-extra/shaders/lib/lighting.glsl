// How a surface is lit. Needs globals.glsl, color.glsl and noise.glsl.

#include "color.glsl"
#include "noise.glsl"
#include "light_colors.glsl"

// The game's own response of light level (0..1) to brightness: gentle at first, steep near the top.
float mx_light_curve(float level) {
    level = clamp(level, 0.0, 1.0);
    return level / (4.0 - 3.0 * level);
}

// How much a surface is turned to the light, 0..1. `wrap` above zero lets light reach a little way round to the
// far side, which is how leaves and grass look, being thin enough for light to pass through.
float mx_facing(vec3 normal, float wrap) {
    return clamp((dot(normal, MxLightDir.xyz) + wrap) / (1.0 + wrap), 0.0, 1.0);
}

// Sunlight that has come through a water surface: the ripples gather it into moving bright lines.
float mx_caustic(vec2 world, float time) {
    float a = mx_noise(world * 1.7 + vec2(time * 0.45, time * 0.30));
    float b = mx_noise(world * 2.9 - vec2(time * 0.55, -time * 0.40));
    float lines = pow(1.0 - abs(a - b), 6.0);
    return 0.55 + 1.9 * lines;
}

// The color of the block light reaching `position` (camera-relative), as linear light at full strength.
// Warm torchlight unless the grid says the light nearby comes from something else.
vec3 mx_block_light_color(vec3 position) {
    vec3 warm = vec3(1.0, 0.46, 0.15);
    if (MxFeatures.z < 0.5) {
        return warm;
    }
    vec4 nearby = mx_light_color_at(position);
    return mix(warm, mx_to_linear(nearby.rgb), nearby.a);
}

// `albedo`: linear surface color. `normal`: world space. `position`: camera-relative world space. `light`:
// block light and sky light, each 0..1. `facing`: from mx_facing. `shadow`: 1 where the sun or moon reaches, 0
// where something is in the way.
vec3 mx_lit(vec3 albedo, vec3 normal, vec3 position, vec2 light, float facing, float shadow) {
    // Sky light falls off more gently than the game's curve alone, or shade under trees and shallow water
    // would be far darker than the open ground beside it.
    float sky = mix(mx_light_curve(light.y), light.y * light.y, 0.5);
    float block = mx_light_curve(light.x);

    // Direct light. Where no sky light reaches at all (deep caves) there is none, whatever the shadow map says:
    // the map only covers the ground near the player and cannot know about a mountain far overhead.
    float open = smoothstep(0.08, 0.5, light.y);
    float direct_amount = facing * shadow * open;
    if (MxParams2.w > 0.5) {
        // Seen from under water, sunlight arrives through the surface.
        direct_amount *= mx_caustic((position + MxCameraPos.xyz).xz, MxCameraPos.w);
    }
    vec3 direct = MxLightColor.rgb * direct_amount;

    // Light that comes from all around has no direction to tell a block's sides apart by: with the sun
    // straight overhead, or by torchlight in a cave, every wall would be the same flat tone. So the sides
    // facing east and west are a little darker than those facing north and south, as in the game itself.
    float side = 1.0 - 0.14 * normal.x * normal.x - 0.04 * normal.z * normal.z;

    // Light from the whole sky, a little stronger on faces that look up, plus a faint warm fill bounced off
    // sunlit ground onto faces that look down or sideways.
    vec3 ambient = MxSkyAmbient.rgb * sky * (0.62 + 0.38 * normal.y) * side;
    ambient += MxLightColor.rgb * sky * 0.045 * (1.0 - 0.6 * normal.y);

    // Torches, lava and the like, in the color of whatever is giving the light, and brighter still right next
    // to the source. MxBlockLight.w carries the torch flicker.
    vec3 glow = mx_block_light_color(position) * 3.3 * MxBlockLight.w * block * (0.5 + 0.5 * light.x);
    glow *= side * (0.88 + 0.12 * normal.y);

    vec3 total = direct + ambient + glow + MxMinAmbient.rgb;
    total *= 1.0 - clamp(MxMinAmbient.w, 0.0, 0.85);
    return albedo * total;
}

// The sun's or moon's highlight on a surface. `gloss` 0 is matte, 1 a mirror-like polish. `tint` colors the
// highlight (white for most things, the surface's own color for metal).
vec3 mx_specular(vec3 normal, vec3 position, float gloss, vec3 tint, float shadow, vec2 light) {
    if (gloss <= 0.0) {
        return vec3(0.0);
    }
    vec3 to_eye = -normalize(position);
    vec3 half_way = normalize(MxLightDir.xyz + to_eye);
    float sharpness = mix(12.0, 400.0, gloss * gloss);
    float spot = pow(max(dot(normal, half_way), 0.0), sharpness) * (sharpness + 8.0) / 25.0;
    // More of the light is mirrored at a grazing angle, as on any smooth surface.
    float grazing = pow(1.0 - max(dot(normal, to_eye), 0.0), 5.0);
    float strength = mix(0.04, 1.0, grazing) * gloss;
    float open = smoothstep(0.08, 0.5, light.y);
    return MxLightColor.rgb * tint * spot * strength * shadow * open * MxLightDir.w;
}
