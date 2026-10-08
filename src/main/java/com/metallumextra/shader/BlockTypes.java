package com.metallumextra.shader;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;

/**
 * What kind of block a piece of chunk mesh came from, as far as the terrain shader cares: it makes water
 * reflect, plants sway, lamps glow and polished things shine. It rides in the alpha byte of each vertex's color,
 * which Sodium writes but nothing reads: terrain is opaque, or cut out by its texture's own alpha (see
 * {@code lib/block_types.glsl}). Sodium copies the color along when it re-sorts translucent quads, so the kind
 * survives that too.
 * <p>
 * The kind of the block being meshed is kept per thread, since chunks are meshed on several threads at once.
 */
public final class BlockTypes {
    public static final int NONE = 0;
    public static final int WATER = 1;
    public static final int LAVA = 2;
    public static final int LEAVES = 3;
    /** A plant rooted in the ground: its top sways, its foot stays put. */
    public static final int PLANT = 4;
    /** The upper half of a two-block plant: all of it sways, since its foot sits on a top that sways. */
    public static final int PLANT_UPPER = 5;
    /** Smooth and polished things: ice, glass, polished stone, quartz, prismarine. */
    public static final int SHINY = 6;
    /** Metal: blocks of iron, gold, copper and so on. */
    public static final int METAL = 7;
    /** {@code GLOWING + 16 * lightColor + lightLevel}, light level 1 to 15. */
    public static final int GLOWING = 32;

    /** The colors a light-giving block can have; {@link #LIGHT_COLORS} holds them in this order, as sRGB bytes. */
    public static final int LIGHT_WARM = 0;
    public static final int LIGHT_FIRE = 1;
    public static final int LIGHT_SOUL = 2;
    public static final int LIGHT_SEA = 3;
    public static final int LIGHT_AMETHYST = 4;
    public static final int LIGHT_WHITE = 5;
    public static final int LIGHT_REDSTONE = 6;
    public static final int LIGHT_PORTAL = 7;
    public static final int[][] LIGHT_COLORS = {
            {255, 180, 110}, {255, 125, 55}, {95, 175, 255}, {125, 230, 255},
            {190, 120, 255}, {235, 240, 255}, {255, 75, 55}, {205, 95, 255}};

    private static final ThreadLocal<int[]> CURRENT = ThreadLocal.withInitial(() -> new int[1]);

    private BlockTypes() {
    }

    public static void begin(final BlockState state) {
        CURRENT.get()[0] = Shaders.active() ? of(state) : NONE;
    }

    public static void begin(final FluidState fluid) {
        CURRENT.get()[0] = !Shaders.active() ? NONE : fluid.is(FluidTags.WATER) ? WATER : fluid.is(FluidTags.LAVA) ? LAVA : NONE;
    }

    public static void end() {
        CURRENT.get()[0] = NONE;
    }

    /** The kind of the block this thread is meshing right now. */
    public static int current() {
        return CURRENT.get()[0];
    }

    private static int of(final BlockState state) {
        int light = state.getLightEmission();
        if (light > 0) return GLOWING + 16 * lightColor(state) + Math.min(light, 15);
        Block block = state.getBlock();
        if (block instanceof LeavesBlock || block instanceof VineBlock) return LEAVES;
        if (block instanceof DoublePlantBlock) {
            return state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER ? PLANT_UPPER : PLANT;
        }
        if (block instanceof VegetationBlock || block instanceof SugarCaneBlock) return PLANT;
        String name = name(block);
        if (name.endsWith("_block") && (name.startsWith("iron") || name.startsWith("gold") || name.startsWith("copper") || name.contains("_copper")
                || name.startsWith("netherite") || name.startsWith("diamond") || name.startsWith("emerald") || name.startsWith("lapis"))
                || name.equals("anvil") || name.contains("chain") || name.contains("iron_bars") || name.contains("cauldron") || name.contains("rail")) {
            return METAL;
        }
        if (name.contains("ice") || name.contains("glass") || name.contains("polished") || name.startsWith("smooth_") || name.contains("quartz")
                || name.contains("obsidian") || name.contains("glazed") || name.contains("prismarine") || name.endsWith("_tiles") || name.contains("amethyst")
                || name.contains("marble") || name.contains("crystal")) {
            return SHINY;
        }
        return NONE;
    }

    /** The color of the light a light-giving block gives, by what it is. */
    public static int lightColor(final BlockState state) {
        String name = name(state.getBlock());
        if (name.contains("soul")) return LIGHT_SOUL;
        if (name.contains("lava") || name.contains("magma") || name.contains("fire") || name.contains("furnace") || name.contains("smoker")) return LIGHT_FIRE;
        if (name.contains("sea_lantern") || name.contains("conduit") || name.contains("prismarine")) return LIGHT_SEA;
        if (name.contains("amethyst")) return LIGHT_AMETHYST;
        if (name.contains("end_rod") || name.contains("beacon") || name.contains("froglight") || name.contains("dragon_egg")) return LIGHT_WHITE;
        if (name.contains("redstone")) return LIGHT_REDSTONE;
        if (name.contains("portal") || name.contains("crying_obsidian") || name.contains("respawn_anchor") || name.contains("enchanting")
                || name.contains("sculk") || name.contains("ender")) return LIGHT_PORTAL;
        return LIGHT_WARM;
    }

    private static String name(final Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).getPath();
    }
}
