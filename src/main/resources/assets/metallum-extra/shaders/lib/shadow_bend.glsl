// The shadow map is drawn fish-eyed: stretched near the middle (around the player) and squeezed towards the edge,
// so shadows are sharp close up without a bigger map. Drawing and reading bend coordinates the same way.
const float MX_SHADOW_BEND = 0.82;

vec2 mx_shadow_bend(vec2 clip) {
    return clip / (length(clip) * MX_SHADOW_BEND + (1.0 - MX_SHADOW_BEND));
}
