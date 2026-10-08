// The sky, as linear light, in any direction. Terrain fog fades to this same function, so distant land melts
// into the sky behind it with no visible seam. Needs globals.glsl and noise.glsl.

#include "noise.glsl"

float mx_hash3(vec3 cell) {
    return fract(sin(dot(cell, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

// The night: a field of fine stars, the band of the Milky Way, and a halo round the moon. All of it scaled by
// the game's own star brightness, so it fades in at dusk and out at dawn the way the game's stars do.
vec3 mx_night_sky(vec3 direction) {
    float stars = MxSkyZenith.w;
    if (stars <= 0.0 || direction.y < -0.1) {
        return vec3(0.0);
    }
    vec3 color = vec3(0.0);

    // Stars: one per cell of a fine grid over the sphere, where the cell's random number allows, and only a
    // little way off the cell's middle so they do not line up.
    vec3 scaled = direction * 90.0;
    vec3 cell = floor(scaled);
    float chance = mx_hash3(cell);
    if (chance > 0.80) {
        vec3 middle = cell + 0.5 + (vec3(mx_hash3(cell + 1.3), mx_hash3(cell + 2.7), mx_hash3(cell + 4.1)) - 0.5) * 0.6;
        float gap = length(scaled - middle);
        float size = 0.18 + 0.14 * mx_hash3(cell + 9.0);
        float point = smoothstep(size, 0.0, gap);
        float twinkle = 0.75 + 0.25 * sin(MxCameraPos.w * (2.0 + 4.0 * mx_hash3(cell + 7.7)) + chance * 40.0);
        float brightness = pow((chance - 0.80) / 0.20, 2.2) * 1.4;
        vec3 star_color = mix(vec3(1.0, 0.9, 0.78), vec3(0.78, 0.86, 1.0), mx_hash3(cell + 5.5));
        color += star_color * point * brightness * twinkle;
    }

    // The Milky Way: a band around a great circle tilted across the sky, with the clumps and dark lanes of
    // noise along it.
    vec3 band_axis = normalize(vec3(0.55, 0.45, 0.70));
    float off_band = dot(direction, band_axis);
    float along = atan(dot(direction, normalize(cross(band_axis, vec3(0.0, 1.0, 0.0)))), dot(direction, normalize(cross(cross(band_axis, vec3(0.0, 1.0, 0.0)), band_axis))));
    float band = exp(-off_band * off_band * 20.0);
    // The same scale along the band as across it: stretched, the clumps drew out into streaks that read as rays.
    float clumps = mx_noise(vec2(along * 9.0, off_band * 9.0)) * 0.7 + mx_noise(vec2(along * 23.0, off_band * 23.0)) * 0.3;
    float lanes = smoothstep(0.35, 0.75, mx_noise(vec2(along * 14.0 + 3.0, off_band * 14.0)));
    color += vec3(0.75, 0.80, 1.0) * band * clumps * (1.0 - 0.6 * lanes) * 0.14;

    // The moon's halo. The moon itself is still the game's.
    float towards_moon = max(dot(direction, MxMoonDir.xyz), 0.0);
    float halo = pow(towards_moon, 180.0) * 0.5 + pow(towards_moon, 14.0) * 0.045;
    color += vec3(0.62, 0.70, 0.95) * halo * MxMoonDir.w;

    // Starlight thins out towards the horizon, where there is more air in the way.
    return color * stars * smoothstep(-0.1, 0.25, direction.y);
}

vec3 mx_sky(vec3 direction) {
    float up = direction.y;
    vec3 zenith = MxSkyZenith.rgb;
    vec3 horizon = MxSkyHorizon.rgb;

    // The game's sky color overhead, its fog color at the horizon, and a slightly dimmer haze below it.
    vec3 color = mix(horizon, zenith, smoothstep(0.0, 0.42, up));
    color = mix(color, horizon * 0.82, smoothstep(0.0, -0.35, up));

    // Sunrise and sunset: a band of the game's sunset color low in the sky, strongest towards the sun.
    vec2 flat_dir = normalize(direction.xz + vec2(1.0e-5));
    vec2 flat_sun = normalize(MxSunDir.xz + vec2(1.0e-5));
    float towards = dot(flat_dir, flat_sun) * 0.5 + 0.5;
    float band = pow(1.0 - min(abs(up), 1.0), 5.0);
    color = mix(color, MxSunsetColor.rgb * 1.25, clamp(MxSunsetColor.a * band * towards * towards, 0.0, 1.0));

    // Glow around the sun (the sun and moon themselves are still the game's).
    float facing = max(dot(direction, MxSunDir.xyz), 0.0);
    float glow = pow(facing, 24.0) * 0.10 + pow(facing, 220.0) * 0.55;
    color += MxLightColor.rgb * MxLightColor.w * MxSunDir.w * glow * (1.0 - 0.8 * MxSkyAmbient.w);

    color *= 1.3;
    color += mx_night_sky(direction);
    return color;
}

// The direction a pixel of the world image looks in, in world space. Two points along the pixel's line of
// sight are unprojected and the direction taken between them: the projection carries the walking bob, a small
// shift of the camera, and a single unprojected point would carry that shift too, swinging the sky with each step.
vec3 mx_view_direction(vec2 pixel) {
    vec2 ndc = pixel * MxScreen.zw * 2.0 - 1.0;
    vec4 near = MxProjInv * vec4(ndc, 0.9, 1.0);
    vec4 far = MxProjInv * vec4(ndc, 0.1, 1.0);
    return normalize(mat3(MxViewInv) * (far.xyz / far.w - near.xyz / near.w));
}
