#version 330

#include "globals.glsl"
#include "color.glsl"
#include "noise.glsl"
#include "sky.glsl"

in vec2 texCoord;
out vec4 fragColor;

// The whole sky in one pass, where the game draws a flat disc over a fog-colored background.
void main() {
    vec3 direction = mx_view_direction(gl_FragCoord.xy);
    // A little noise hides the steps an 8-bit image would otherwise show in so smooth a gradient.
    float grain = (mx_dither(gl_FragCoord.xy) - 0.5) / 255.0;
    fragColor = vec4(mx_encode(mx_sky(direction)) + grain, 1.0);
}
