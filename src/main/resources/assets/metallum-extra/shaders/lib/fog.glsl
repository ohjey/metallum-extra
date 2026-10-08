// Fog. Needs globals.glsl, color.glsl and sky.glsl.

float mx_fog_ramp(float distance, float start, float end) {
    return clamp((distance - start) / max(end - start, 1.0e-4), 0.0, 1.0);
}

// The game's two fogs together: the one of the surroundings (rain, water, lava) by straight-line distance,
// and the one that hides the edge of the loaded world by distance along the ground.
float mx_fog_amount(vec3 position, vec2 surroundings, vec2 world_edge) {
    float flat_distance = max(length(position.xz), abs(position.y));
    return max(mx_fog_ramp(length(position), surroundings.x, surroundings.y),
               mx_fog_ramp(flat_distance, world_edge.x, world_edge.y));
}

// Under the open sky: the game's fog together with a thin haze that thickens with distance and rain.
float mx_fog_total(vec3 position, float amount, float game_fog_alpha) {
    float haze = 1.0 - exp(-length(position) * (0.0004 + 0.0035 * MxSkyAmbient.w));
    // The game's fog rises in a straight line from the camera out. Squared, it leaves what is near clear and
    // still closes fully at the edge of the loaded world.
    amount *= game_fog_alpha;
    return max(amount * amount, haze * 0.7);
}

// Lit linear color -> the display color to write, with fog applied.
// Under the open sky the fog is the sky itself, so distant land melts into the sky behind it. Anywhere else
// (underwater, the Nether, blindness) it is the game's own fog color, exactly.
vec3 mx_fogged(vec3 lit, vec3 position, float amount, vec4 game_fog) {
    if (MxSkyHorizon.w > 0.5) {
        return mix(mx_encode(lit), game_fog.rgb, amount * game_fog.a);
    }
    return mx_encode(mix(lit, mx_sky(normalize(position)), mx_fog_total(position, amount, game_fog.a)));
}
