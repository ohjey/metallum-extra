#version 330

// Softens the half-size effects image without smearing across depth edges.

#include "globals.glsl"

uniform sampler2D InSampler;
uniform sampler2D MxSceneDepth;

in vec2 texCoord;
out vec4 fragColor;

// The depth an effects pixel was worked out from: the first of the four depth texels under it.
float linear_depth(ivec2 pixel) {
    ivec2 texel = clamp(pixel * 2, ivec2(0), textureSize(MxSceneDepth, 0) - 1);
    float depth = texelFetch(MxSceneDepth, texel, 0).r;
    if (depth <= 0.0) {
        return 1.0e5;
    }
    vec4 view = MxProjInv * vec4((vec2(texel) + 0.5) * MxScreen.zw * 2.0 - 1.0, depth, 1.0);
    return -view.z / view.w;
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float here = linear_depth(pixel);
    vec4 sum = vec4(0.0);
    float weights = 0.0;
    for (int y = -2; y <= 2; y++) {
        for (int x = -2; x <= 2; x++) {
            vec2 at = texCoord + vec2(x, y) * texel;
            float there = linear_depth(pixel + ivec2(x, y));
            float weight = exp(-abs(there - here) / (0.03 * here + 0.15)) * exp(-float(x * x + y * y) * 0.15);
            sum += textureLod(InSampler, at, 0.0) * weight;
            weights += weight;
        }
    }
    fragColor = sum / max(weights, 1.0e-4);
}
