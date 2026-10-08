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
in vec2 texCoord0;
in vec4 vertexColor;

in vec3 mxPosition;
in vec2 mxLight;
in vec4 mxColor;

out vec4 fragColor;

void main() {
    vec4 texel = texture(Sampler0, texCoord0);

    if (MxParams.x == MX_PHASE_WORLD) {
        vec4 color = texel * mxColor * ColorModulator;
        if (color.a < 0.1) {
            discard;
        }
        // A particle always faces the camera, so it has no side to turn to the light: light it as if it faced
        // halfway between straight up and the sun.
        vec3 normal = normalize(MxLightDir.xyz + vec3(0.0, 1.0, 0.0));
        float facing = mx_facing(normal, 0.0);
        vec3 lit = mx_lit(mx_to_linear(color.rgb), normal, mxPosition, mxLight, facing, facing > 0.0 ? mx_shadow(mxPosition, normal) : 1.0);
        float fog = mx_fog_amount(mxPosition, vec2(FogEnvironmentalStart, FogEnvironmentalEnd), vec2(FogRenderDistanceStart, FogRenderDistanceEnd));
        fragColor = vec4(mx_fogged(lit, mxPosition, fog, FogColor), color.a);
        return;
    }

    vec4 color = texel * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = mx_game_fog(color, sphericalVertexDistance, cylindricalVertexDistance);
}
