layout(std140) uniform Lighting {
    vec3 Light0_Direction;
    vec3 Light1_Direction;
};

// The game's fixed pair of lights on models: 40% everywhere plus 60% for each light a face is turned to.
float mx_game_shade(float towards_first, float towards_second) {
    return min(1.0, (max(towards_first, 0.0) + max(towards_second, 0.0)) * 0.6 + 0.4);
}
