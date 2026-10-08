#version 330

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

// Halves the image again: the center plus four diagonal samples, each of which already averages four texels.
void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    vec3 sum = texture(InSampler, texCoord).rgb * 4.0;
    sum += texture(InSampler, texCoord + texel * vec2(-1.0, -1.0)).rgb;
    sum += texture(InSampler, texCoord + texel * vec2(1.0, -1.0)).rgb;
    sum += texture(InSampler, texCoord + texel * vec2(-1.0, 1.0)).rgb;
    sum += texture(InSampler, texCoord + texel * vec2(1.0, 1.0)).rgb;
    fragColor = vec4(sum / 8.0, 1.0);
}
