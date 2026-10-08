#version 330

#include "globals.glsl"
#include "color.glsl"

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

// The blurred light, rolled off again the way bloom_prefilter.fsh undid it, to be laid over the world image as
// a thin layer: each pixel becomes mostly itself and a little of the light around it. Where the picture is even
// that changes nothing; next to something bright, the brightness spills over.
void main() {
    // Five levels were added together on the way up.
    vec3 light = texture(InSampler, texCoord).rgb * 0.2;
    float level = max(light.r, max(light.g, light.b));
    vec3 shown = light * ((1.0 - exp(-level)) / max(level, 1.0e-4));
    float share = 0.18 * MxParams.z;
    fragColor = vec4(mx_to_display(shown) * share, share);
}
