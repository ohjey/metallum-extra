// Cheap noise, shared by water, caustics and anything else that needs some randomness.

float mx_hash(vec2 cell) {
    return fract(sin(dot(cell, vec2(127.1, 311.7))) * 43758.5453);
}

// Smooth noise, 0..1: a random value at each whole coordinate, blended in between.
float mx_noise(vec2 point) {
    vec2 cell = floor(point);
    vec2 blend = fract(point);
    blend = blend * blend * (3.0 - 2.0 * blend);
    return mix(mix(mx_hash(cell), mx_hash(cell + vec2(1.0, 0.0)), blend.x),
               mix(mx_hash(cell + vec2(0.0, 1.0)), mx_hash(cell + vec2(1.0, 1.0)), blend.x), blend.y);
}

float mx_dither(vec2 pixel) {
    return fract(52.9829189 * fract(dot(pixel, vec2(0.06711056, 0.00583715))));
}
