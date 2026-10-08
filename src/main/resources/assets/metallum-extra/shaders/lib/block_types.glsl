// The kind of block a piece of terrain came from. BlockTypes.java writes it into the alpha byte of each vertex's
// color and must agree with this file.
const uint MX_BLOCK_WATER = 1u;
const uint MX_BLOCK_LAVA = 2u;
const uint MX_BLOCK_LEAVES = 3u;
const uint MX_BLOCK_PLANT = 4u;
const uint MX_BLOCK_PLANT_UPPER = 5u;
const uint MX_BLOCK_SHINY = 6u;
const uint MX_BLOCK_METAL = 7u;
// 32 and up: a block that gives off light; bits 0-3 are its light level, bits 4-6 the color of its light.
const uint MX_BLOCK_GLOWING = 32u;

float mx_block_light_level(uint type) {
    return float(type & 15u) / 15.0;
}
