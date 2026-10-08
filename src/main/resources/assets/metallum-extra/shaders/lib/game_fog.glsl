layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

// The game's fog, for when its replaced shaders draw something that is not the world (the hand, the menus).
vec4 mx_game_fog(vec4 color, float spherical, float cylindrical) {
    float amount = max(
        clamp((spherical - FogEnvironmentalStart) / max(FogEnvironmentalEnd - FogEnvironmentalStart, 1.0e-4), 0.0, 1.0),
        clamp((cylindrical - FogRenderDistanceStart) / max(FogRenderDistanceEnd - FogRenderDistanceStart, 1.0e-4), 0.0, 1.0));
    return vec4(mix(color.rgb, FogColor.rgb, amount * FogColor.a), color.a);
}
