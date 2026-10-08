#version 330

// Lays the effects over the world image: occlusion darkens, sun rays add light.

#include "globals.glsl"
#include "color.glsl"

uniform sampler2D InSampler;
uniform sampler2D MxSceneColor;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 effects = textureLod(InSampler, texCoord, 0.0);
    vec3 color = textureLod(MxSceneColor, texCoord, 0.0).rgb;
    vec3 linear = mx_to_linear(color) * effects.a;
    vec3 rays = 1.0 - exp(-effects.rgb * MxParams.y);
    // Rays are screen-blended, so they brighten without clipping; in linear light, so they lift what is dark
    // by no more than they add to what is bright.
    vec3 out_color = mx_to_display(linear + rays * (1.0 - linear));
    if (MxFeatures.w == 1.0) {
        out_color = vec3(effects.a);
    } else if (MxFeatures.w == 3.0) {
        out_color = mx_to_display(rays);
    }
    fragColor = vec4(out_color, 1.0);
}
