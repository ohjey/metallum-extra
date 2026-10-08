#version 330

#include "globals.glsl"
#include "color.glsl"
#include "noise.glsl"
#include "sky.glsl"
#include "game_fog.glsl"

in float vertexDistance;
in vec4 vertexColor;
in vec3 mxPosition;
flat in vec3 mxNormal;

out vec4 fragColor;

void main() {
    // The game thins its clouds out with distance, up to where it stops drawing them.
    float thin = clamp(vertexDistance / max(FogCloudsEnd, 1.0e-4), 0.0, 1.0);
    if (MxParams.x != MX_PHASE_WORLD || MxSkyHorizon.w > 0.5) {
        fragColor = vec4(vertexColor.rgb, vertexColor.a * (1.0 - thin));
        return;
    }

    vec3 normal = mxNormal;
    if (dot(normal, mxPosition) > 0.0) {
        normal = -normal;
    }
    vec3 view = normalize(mxPosition);

    // A cloud is a thick body of mist. Light goes round and through it, so the side turned from the sun is
    // dimmer but never dark, and its edge shines when the sun is close behind it.
    float towards = dot(normal, MxLightDir.xyz);
    float direct = 0.34 + 0.66 * clamp(towards * 0.5 + 0.5, 0.0, 1.0);
    float lining = pow(max(dot(view, MxLightDir.xyz), 0.0), 10.0);
    vec3 light = MxLightColor.rgb * (direct + 0.6 * lining)
               + MxSkyAmbient.rgb * (0.72 + 0.28 * normal.y)
               + MxMinAmbient.rgb;
    vec3 lit = vec3(0.50) * light * (1.0 - clamp(MxMinAmbient.w, 0.0, 0.85));
    // Rain clouds are a closed deck with no sunlit side: the color of the rainy sky, a shade darker.
    lit = mix(lit, MxSkyHorizon.rgb * 0.9, smoothstep(0.0, 0.7, MxSkyAmbient.w));

    // Far clouds sink into the haze of the sky behind them.
    float haze = 1.0 - exp(-vertexDistance * 0.0009);
    lit = mix(lit, mx_sky(view), haze);

    fragColor = vec4(mx_encode(lit), vertexColor.a * (1.0 - thin));
}
