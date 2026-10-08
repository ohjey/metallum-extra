// A lookup in the game's 16x16 light map. `coordinate` is block light and sky light, each 0..240.
vec4 mx_game_light(sampler2D light_map, ivec2 coordinate) {
    return texture(light_map, clamp((vec2(coordinate) + 8.0) / 256.0, vec2(1.0 / 32.0), vec2(31.0 / 32.0)));
}
