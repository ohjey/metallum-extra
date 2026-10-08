// Sodium's chunk mesh: how its packed vertices unpack. For vertex shaders only.

layout(push_constant) uniform PC {
    vec3 u_RegionOffset;
    int u_CurrentTime;
    uint u_RegionID;
};

in uvec2 a_Position;
in vec4 a_Color;
in uvec2 a_TexCoord;
in uvec4 a_LightAndData;

// Position inside the region's 8x4x8 block of sections: three 20-bit numbers, their high and low halves in
// separate words, covering -8..24 blocks.
vec3 mx_chunk_position() {
    uvec3 high = (uvec3(a_Position.x) >> uvec3(0u, 10u, 20u)) & 0x3FFu;
    uvec3 low = (uvec3(a_Position.y) >> uvec3(0u, 10u, 20u)) & 0x3FFu;
    vec3 local = vec3((high << 10u) | low) * (32.0 / 1048576.0) - 8.0;

    // Which section of the region this vertex is in.
    uint section = a_LightAndData.w;
    vec3 section_origin = vec3((section >> 5u) & 7u, section & 3u, (section >> 2u) & 7u) * 16.0;
    return local + section_origin + u_RegionOffset;
}

// Texture coordinate: 15 bits each, with one more bit saying which way the coordinate was nudged towards the
// middle of its sprite; the nudge itself is applied here.
vec2 mx_chunk_texcoord(vec2 nudge) {
    vec2 coordinate = vec2(a_TexCoord & 0x7FFFu) / 32768.0;
    vec2 direction = vec2(a_TexCoord >> 15u) * 2.0 - 1.0;
    return coordinate + direction * nudge;
}

// Whether this vertex is in the upper half of its quad's texture (the nudge bit says which side of the middle
// of the texture area a vertex is on).
bool mx_chunk_texture_top() {
    return (a_TexCoord.y >> 15u) == 0u;
}

// Block light and sky light, 0..1 each.
vec2 mx_chunk_light() {
    return clamp((vec2(a_LightAndData.xy) - 8.0) / 240.0, 0.0, 1.0);
}

// The kind of block this vertex belongs to (see block_types.glsl).
uint mx_chunk_type() {
    return uint(a_Color.a * 255.0 + 0.5);
}

// The vertex's color without the kind that rides in its alpha.
vec4 mx_chunk_color() {
    return vec4(a_Color.rgb, 1.0);
}

uint mx_chunk_section() {
    return a_LightAndData.w;
}
