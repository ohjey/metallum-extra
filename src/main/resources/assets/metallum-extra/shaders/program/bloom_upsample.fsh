#version 330

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

// Grows the smaller level back up with a 3x3 tent, to be added onto the level above it.
void main() {
    vec2 texel = 1.0 / vec2(textureSize(InSampler, 0));
    vec3 sum = texture(InSampler, texCoord).rgb * 4.0;
    sum += texture(InSampler, texCoord + texel * vec2(-1.0, 0.0)).rgb * 2.0;
    sum += texture(InSampler, texCoord + texel * vec2(1.0, 0.0)).rgb * 2.0;
    sum += texture(InSampler, texCoord + texel * vec2(0.0, -1.0)).rgb * 2.0;
    sum += texture(InSampler, texCoord + texel * vec2(0.0, 1.0)).rgb * 2.0;
    sum += texture(InSampler, texCoord + texel * vec2(-1.0, -1.0)).rgb;
    sum += texture(InSampler, texCoord + texel * vec2(1.0, -1.0)).rgb;
    sum += texture(InSampler, texCoord + texel * vec2(-1.0, 1.0)).rgb;
    sum += texture(InSampler, texCoord + texel * vec2(1.0, 1.0)).rgb;
    fragColor = vec4(sum / 16.0, 1.0);
}
