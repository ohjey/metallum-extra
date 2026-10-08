// The values Metallum Extra works out once per frame. ShaderGlobals.java writes this block and must list the
// same fields in the same order.
layout(std140) uniform MxGlobals {
    mat4 MxView;            // camera-relative world space -> view space (rotation only)
    mat4 MxViewInv;
    mat4 MxProj;            // the projection the world is drawn with
    mat4 MxProjInv;
    mat4 MxShadowMat;       // camera-relative world space -> shadow map clip space
    vec4 MxLightDir;        // xyz: towards the sun or moon, whichever is lighting the world. w: shadow strength
    vec4 MxLightColor;      // rgb: linear color of that light. w: 1 by day, 0 by night
    vec4 MxSunDir;          // xyz: towards the sun. w: how much of it is above the horizon
    vec4 MxSkyAmbient;      // rgb: linear light from the open sky. w: rain
    vec4 MxBlockLight;      // rgb: linear color of block light at full strength
    vec4 MxMinAmbient;      // rgb: linear light that reaches everywhere. w: darkness effect
    vec4 MxSkyZenith;       // rgb: linear sky color overhead. w: star brightness
    vec4 MxSkyHorizon;      // rgb: linear sky color at the horizon. w: 1 = fog fades to the game's fog color
    vec4 MxSunsetColor;     // rgb: linear sunrise or sunset tint. a: strength
    vec4 MxCameraPos;       // xyz: camera position (wrapped). w: seconds (wrapped)
    vec4 MxScreen;          // xy: world image size. zw: one pixel
    vec4 MxParams;          // x: phase. y: exposure. z: bloom strength. w: one shadow map texel
    vec4 MxParams2;         // x: shadow distance in blocks. y: waving. z: water reflections. w: 1 while the camera is under water
    vec4 MxMoonDir;         // xyz: towards the moon. w: how much of it is above the horizon
    vec4 MxLightGrid;       // xyz: where the light-color grid starts (camera-relative). w: one cell in blocks
    vec4 MxFeatures;        // x: sun rays. y: ambient occlusion. z: colored block light. w: debug view (0 off)
    vec4 MxParams3;         // x: sky light where the camera is, 0..1, smoothed over time. y: edge smoothing. zw: unused
};

const float MX_PHASE_NONE = 0.0;
const float MX_PHASE_WORLD = 1.0;
const float MX_PHASE_HAND = 2.0;
const float MX_PHASE_SHADOW = 3.0;

bool mx_in_world() {
    return MxParams.x == MX_PHASE_WORLD;
}
