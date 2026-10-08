// Shared by the replaced shaders for everything the game itself draws in the world: mobs, block entities, items,
// particles. Needs globals.glsl and game_transforms.glsl.

// Where a vertex is in camera-relative world space. The game's model-view matrix is the camera's rotation,
// sometimes with a transform of the model in front of it; undoing the camera's part leaves world space.
vec3 mx_world_position(vec3 position) {
    return mat3(MxViewInv) * (ModelViewMat * vec4(position, 1.0)).xyz;
}

vec3 mx_world_normal(vec3 normal) {
    return mat3(MxViewInv) * (mat3(ModelViewMat) * normal);
}

#include "shadow_bend.glsl"

// Where a point lands in the shadow map, for drawing into it.
vec4 mx_shadow_clip(vec3 world_position) {
    vec4 clip = MxShadowMat * vec4(world_position, 1.0);
    clip.xy = mx_shadow_bend(clip.xy);
    return clip;
}
