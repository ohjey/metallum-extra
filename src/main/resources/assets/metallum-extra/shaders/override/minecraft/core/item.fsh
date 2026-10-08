#version 330

#include "globals.glsl"
#include "color.glsl"
#include "noise.glsl"
#include "sky.glsl"
#include "shadow.glsl"
#include "lighting.glsl"
#include "fog.glsl"
#include "game_transforms.glsl"
#include "game_fog.glsl"

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

in vec3 mxPosition;
in vec3 mxNormal;
in vec2 mxLight;
in vec4 mxColor;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (texel.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    if (MxParams.x == MX_PHASE_SHADOW) {
        if (texel.a * mxColor.a * ColorModulator.a < 0.1) {
            discard;
        }
        fragColor = vec4(1.0);
        return;
    }

    if (MxParams.x == MX_PHASE_WORLD) {
        vec4 color = texel * mxColor * ColorModulator;
        color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
        vec3 normal = normalize(mxNormal);
        if (dot(normal, mxPosition) > 0.0) {
            normal = -normal;
        }
        float facing = mx_facing(normal, 0.0);
        vec3 lit = mx_lit(mx_to_linear(color.rgb), normal, mxPosition, mxLight, facing, facing > 0.0 ? mx_shadow(mxPosition, normal) : 1.0);
        float fog = mx_fog_amount(mxPosition, vec2(FogEnvironmentalStart, FogEnvironmentalEnd), vec2(FogRenderDistanceStart, FogRenderDistanceEnd));
        fragColor = vec4(mx_fogged(lit, mxPosition, fog, FogColor), color.a);
        return;
    }

    vec4 color = texel * vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
    fragColor = mx_game_fog(color, sphericalVertexDistance, cylindricalVertexDistance);
}
