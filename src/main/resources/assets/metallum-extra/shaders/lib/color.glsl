// Display colors (what the game's textures and its world image hold) and linear light (what lighting is added
// up in). The world image stays in display colors so everything Metallum Extra does not replace still fits in.

vec3 mx_to_linear(vec3 display) {
    return pow(max(display, vec3(0.0)), vec3(2.2));
}

vec3 mx_to_display(vec3 linear) {
    return pow(clamp(linear, 0.0, 1.0), vec3(1.0 / 2.2));
}

float mx_luminance(vec3 linear) {
    return dot(linear, vec3(0.2126, 0.7152, 0.0722));
}

// Bright light rolls off towards white instead of clipping, and slowly: white is only reached at about six
// times the brightness of sunlit grass, so snow and sand in full sun stay short of it and keep their shading.
// A gentle S-curve on top keeps the mid tones from going milky: a touch more depth in the shade, a touch more
// punch in the light.
float mx_tonemap_curve(float x) {
    x *= 1.25;
    float soft = min(x * (1.0 + x / 64.0) / (1.0 + x), 1.0);
    return mix(soft, soft * soft * (3.0 - 2.0 * soft), 0.3);
}

// The curve applied to a color's brightness, with its hue kept: a bright blue sky stays blue instead of turning
// grey-white, which is what rolling each channel off on its own does to it. Only the very brightest light (the
// sun, lava) is allowed to wash towards white, the way film does.
vec3 mx_tonemap(vec3 linear) {
    float brightness = max(mx_luminance(linear), 1.0e-4);
    vec3 by_brightness = linear * (mx_tonemap_curve(brightness) / brightness);
    vec3 by_channel = vec3(mx_tonemap_curve(linear.r), mx_tonemap_curve(linear.g), mx_tonemap_curve(linear.b));
    return mix(by_brightness, by_channel, smoothstep(1.5, 5.0, brightness));
}

// The last touch on a rolled-off color: a little more color than the light alone gives, the way a photograph
// is printed. Rolling bright light off towards white takes some color out; this puts it back.
vec3 mx_grade(vec3 mapped) {
    return clamp(mix(vec3(mx_luminance(mapped)), mapped, 1.10), 0.0, 1.0);
}

// Linear scene light -> the display color written to the world image.
vec3 mx_encode(vec3 linear) {
    vec3 mapped = mx_grade(clamp(mx_tonemap(max(linear, vec3(0.0)) * MxParams.y), 0.0, 1.0));
    return pow(mapped, vec3(1.0 / 2.2));
}
