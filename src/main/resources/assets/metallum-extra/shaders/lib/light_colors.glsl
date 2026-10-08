// The color of block light at a point, from the grid LightColorGrid.java keeps around the player. Needs
// globals.glsl. The grid's depth slices lie side by side in one 2D texture.

uniform sampler2D MxLightColors;

const vec3 MX_GRID_SIZE = vec3(48.0, 32.0, 48.0);

// rgb: the light's color as a display color. a: how sure that is (0 where no light-giving block is near).
vec4 mx_light_color_at(vec3 position) {
    vec3 cell = (position - MxLightGrid.xyz) / MxLightGrid.w - 0.5;
    if (any(lessThan(cell, vec3(0.0))) || any(greaterThan(cell, MX_GRID_SIZE - 1.0))) {
        return vec4(0.0);
    }
    cell.x = min(cell.x, MX_GRID_SIZE.x - 1.02);
    float slice = floor(cell.z);
    float next = min(slice + 1.0, MX_GRID_SIZE.z - 1.0);
    float between = cell.z - slice;
    float row = (cell.y + 0.5) / MX_GRID_SIZE.y;
    vec4 a = textureLod(MxLightColors, vec2((slice * MX_GRID_SIZE.x + cell.x + 0.5) / (MX_GRID_SIZE.x * MX_GRID_SIZE.z), row), 0.0);
    vec4 b = textureLod(MxLightColors, vec2((next * MX_GRID_SIZE.x + cell.x + 0.5) / (MX_GRID_SIZE.x * MX_GRID_SIZE.z), row), 0.0);
    return mix(a, b, between);
}
