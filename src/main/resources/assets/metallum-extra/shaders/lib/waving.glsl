// Wind and water movement, applied to terrain vertices. The shadow pass applies the same movement, so shadows
// follow what casts them. Needs globals.glsl and block_types.glsl.

float mx_water_height(vec2 world, float time) {
    return sin(world.x * 0.82 + world.y * 0.53 + time * 1.45) * 0.5
         + sin(world.x * -1.71 + world.y * 1.27 + time * 1.93) * 0.3
         + sin(world.x * 3.13 + world.y * 2.29 - time * 2.61) * 0.2;
}

// `position` is camera-relative world space. `is_top` says whether the vertex is at the top of its quad's
// texture, which for a plant is the tip of the plant.
vec3 mx_wave(vec3 position, uint type, bool is_top) {
    if (MxParams2.y < 0.5 || type == 0u || type >= MX_BLOCK_GLOWING) {
        return position;
    }
    vec3 world = position + MxCameraPos.xyz;
    float time = MxCameraPos.w;

    if (type == MX_BLOCK_WATER) {
        // Only the water's surface moves. Its sides and bottom end on whole block heights; the surface does not.
        float height = fract(world.y);
        if (height > 0.05 && height < 0.95) {
            position.y += mx_water_height(world.xz, time) * 0.035;
        }
        return position;
    }

    if (type == MX_BLOCK_LEAVES || type == MX_BLOCK_PLANT || type == MX_BLOCK_PLANT_UPPER) {
        // A slow sway shared by everything nearby, with a faster flutter on top; stronger in the rain.
        float wind = 1.0 + 1.5 * MxSkyAmbient.w;
        float sway = sin(world.x * 0.35 + world.z * 0.22 + time * 1.2) + 0.5 * sin(world.x * 0.9 - world.z * 1.3 + time * 2.7);
        float flutter = sin(world.x * 2.3 + world.y * 1.7 + world.z * 2.9 + time * 2.6);
        float amount;
        if (type == MX_BLOCK_LEAVES) {
            amount = 0.015;
        } else if (type == MX_BLOCK_PLANT) {
            amount = is_top ? 0.07 : 0.0;
        } else {
            amount = is_top ? 0.12 : 0.07;
        }
        position.x += (sway + 0.25 * flutter) * amount * wind;
        position.z += (sway * 0.6 - 0.25 * flutter) * amount * wind;
        return position;
    }

    return position;
}
