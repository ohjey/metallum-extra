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

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

in float sphericalVertexDistance;
in float cylindricalVertexDistance;

#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif

#ifndef EMISSIVE
in vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
in vec4 overlayColor;
#endif

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

#ifdef PER_FACE_LIGHTING
    vec4 shaded = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
#else
    vec4 shaded = vertexColor;
#endif
    // `shaded` carries the game's fixed model lighting, `plain` only the model's own tint.
    vec4 plain = mxColor;

#ifdef DISSOLVE
    // A dissolving model shows or hides each texel outright; it is never see-through.
    if (shaded.a < texture(DissolveMaskSampler, texCoord0).a) {
        discard;
    }
    shaded.a = 1.0;
    plain.a = 1.0;
#endif

    if (MxParams.x == MX_PHASE_SHADOW) {
        if (texel.a * plain.a * ColorModulator.a < 0.1) {
            discard;
        }
        fragColor = vec4(1.0);
        return;
    }

    if (MxParams.x == MX_PHASE_WORLD) {
        vec4 color = texel * plain * ColorModulator;
#ifndef NO_OVERLAY
        color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#endif
        vec3 albedo = mx_to_linear(color.rgb);
#ifdef EMISSIVE
        // Glowing parts (eyes, charged creepers) give off their own light.
        vec3 lit = albedo * 1.7;
#else
        vec3 normal = normalize(mxNormal);
        if (dot(normal, mxPosition) > 0.0) {
            normal = -normal;
        }
#ifdef NO_CARDINAL_LIGHTING
        // These are meant to look the same from every side.
        normal = normalize(MxLightDir.xyz * 0.5 + vec3(0.0, 1.0, 0.0));
#endif
        float facing = mx_facing(normal, 0.0);
        vec3 lit = mx_lit(albedo, normal, mxPosition, mxLight, facing, facing > 0.0 ? mx_shadow(mxPosition, normal) : 1.0);
#endif
        float fog = mx_fog_amount(mxPosition, vec2(FogEnvironmentalStart, FogEnvironmentalEnd), vec2(FogRenderDistanceStart, FogRenderDistanceEnd));
        fragColor = vec4(mx_fogged(lit, mxPosition, fog, FogColor), color.a);
        return;
    }

    vec4 color = texel * shaded * ColorModulator;
#ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#endif
#ifndef EMISSIVE
    color *= lightMapColor;
#endif
    fragColor = mx_game_fog(color, sphericalVertexDistance, cylindricalVertexDistance);
}
