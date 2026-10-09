package com.metallumextra;

import com.metallumextra.shader.Shaders;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** The player-facing settings, defined once and shown by both the Sodium video settings page and the Mod Menu screen. */
public final class Settings {
    public enum Impact { LOW, MEDIUM, HIGH, VARIES }

    /** {@code available} is false when the setting cannot do anything in this installation; the tooltip then says why. */
    public record Toggle(String id, String name, String tooltip, Impact impact, boolean needsRestart,
                         BooleanSupplier getter, Consumer<Boolean> setter, boolean available) {
        public Toggle(final String id, final String name, final String tooltip, final Impact impact, final boolean needsRestart,
                      final BooleanSupplier getter, final Consumer<Boolean> setter) {
            this(id, name, tooltip, impact, needsRestart, getter, setter, true);
        }

        /** Everything is on by default except performance logging and the shaders' master switch. */
        public boolean defaultValue() {
            return !id.equals("profiler") && !id.equals("shaders");
        }
    }

    /** A choice among several values, shown as a cycling button. */
    public record Choice<E extends Enum<E>>(String id, String name, String tooltip, Impact impact, Class<E> type,
                                           java.util.function.Supplier<E> getter, Consumer<E> setter, java.util.function.Function<E, String> label) {
    }

    private Settings() {
    }

    /** The shader quality preset. */
    public static Choice<Quality> shaderQuality() {
        ExtraConfig c = ExtraConfig.get();
        return new Choice<>("shader_quality", "Shader Quality",
                "A preset for the shader settings below. Low: shadows at a short distance, no reflections, no "
                        + "ambient occlusion. Medium: shadows, reflections and ambient occlusion. High: everything, "
                        + "with sun rays and shadows further out. Ultra: the sharpest and furthest shadows, for the "
                        + "strongest machines. Changing a setting below afterwards makes this read Custom.",
                Impact.VARIES, Quality.class, c::shaderQuality, c::setShaderQuality, quality -> quality.label);
    }

    /** The shader settings, shown on the Shader Options screen. All of these switch on and off immediately; shaders themselves
     * are switched on and off from the Shaders screen or its key. */
    public static List<Toggle> shaders() {
        ExtraConfig c = ExtraConfig.get();
        return List.of(
                new Toggle("shader_shadows", "Shadows",
                        "The sun and moon cast shadows. This draws the nearby world a second time each frame, so "
                                + "it is the most expensive shader setting. Only applies while Shaders is on.",
                        Impact.HIGH, false, () -> c.shaderShadows, c::setShaderShadows),
                new Toggle("shader_bloom", "Glow",
                        "A soft glow around bright things such as the sun, lava and torches. Only applies while "
                                + "Shaders is on.",
                        Impact.LOW, false, () -> c.shaderBloom, c::setShaderBloom),
                new Toggle("shader_reflections", "Water Reflections",
                        "Water reflects the land and sky around it. Only applies while Shaders is on.",
                        Impact.MEDIUM, false, () -> c.shaderWaterReflections, c::setShaderWaterReflections),
                new Toggle("shader_waving", "Moving Water and Plants",
                        "Water ripples, and leaves and plants sway in the wind. Only applies while Shaders is on.",
                        Impact.LOW, false, () -> c.shaderWaving, c::setShaderWaving),
                new Toggle("shader_sun_rays", "Sun Rays",
                        "Shafts of sunlight through trees, cave openings and morning haze. Only applies while "
                                + "Shaders and Shadows are on.",
                        Impact.MEDIUM, false, () -> c.shaderSunRays, c::setShaderSunRays),
                new Toggle("shader_ambient_occlusion", "Ambient Occlusion",
                        "Corners, crevices and the ground under things are a little darker, which makes the world "
                                + "look less flat. Only applies while Shaders is on.",
                        Impact.MEDIUM, false, () -> c.shaderAmbientOcclusion, c::setShaderAmbientOcclusion),
                new Toggle("shader_colored_light", "Colored Light",
                        "Torches glow orange, soul fire blue, sea lanterns teal, amethyst purple, and so on, and they "
                                + "color what they light. Only applies while Shaders is on.",
                        Impact.LOW, false, () -> c.shaderColoredLight, c::setShaderColoredLight),
                new Toggle("shader_smooth_edges", "Smooth Edges",
                        "Blends away the stair steps along the edges of blocks, leaves and far-off terrain. Only "
                                + "applies while Shaders is on.",
                        Impact.LOW, false, () -> c.shaderSmoothEdges, c::setShaderSmoothEdges));
    }

    /** What the shaders say in the chat. */
    public static List<Toggle> messages() {
        ExtraConfig c = ExtraConfig.get();
        return List.of(
                new Toggle("shader_messages", "Shader Chat Messages",
                        "Lines in the chat from the shader keys and from shader packs: what was toggled or reloaded, "
                                + "and why a pack could not be used. Off: the shaders never write to the chat (a "
                                + "failure is still in the log and shown in the shader menu).",
                        Impact.LOW, false, () -> c.shaderMessages, c::setShaderMessages));
    }

    /** Stutter and frame-rate fixes. All of these switch on and off immediately. */
    public static List<Toggle> smoothness() {
        ExtraConfig c = ExtraConfig.get();
        boolean builtIn = MetallumVersion.WRITES_BUFFERS_DIRECTLY;
        return List.of(
                new Toggle("non_blocking_present", "Unlocked Frame Rate",
                        "With VSync off, stops the game from pausing to wait for your screen. Gives higher FPS and "
                                + "removes small, frequent stutters. Frames your screen has no time to show are "
                                + "skipped. Does nothing while VSync is on.",
                        Impact.HIGH, false, () -> c.nonBlockingPresent, c::setNonBlockingPresent),
                new Toggle("fast_section_recenter", "Smooth Chunk Crossing",
                        "Removes a short freeze each time you move into a new chunk. The higher your render "
                                + "distance, the bigger that freeze is, so this matters most at very high render "
                                + "distances (for example with Bobby).",
                        Impact.VARIES, false, () -> c.fastSectionRecenter, c::setFastSectionRecenter),
                new Toggle("spread_sodium_cleanup", "Smooth Memory Cleanup",
                        "Removes a stutter that shows up every 20-30 seconds while lots of chunks are loading. "
                                + "Sodium tidies up used chunk memory all in one frame; this spreads that work over "
                                + "many frames so you do not feel it.",
                        Impact.MEDIUM, false, () -> c.spreadSodiumCleanup, c::setSpreadSodiumCleanup),
                new Toggle("direct_buffer_upload", "Faster Small Uploads",
                        (builtIn ? "NOT NEEDED: this version of Metallum does this by itself. " : "")
                                + "Sends small pieces of render data straight to memory instead of through an extra GPU "
                                + "step. A small improvement; safe to leave on.",
                        Impact.LOW, false, () -> c.directBufferUpload, c::setDirectBufferUpload, !builtIn));
    }

    /** Diagnostics. Read once at startup. */
    public static List<Toggle> troubleshooting() {
        ExtraConfig c = ExtraConfig.get();
        return List.of(
                new Toggle("profiler", "Performance Logging",
                        "For troubleshooting only. Leave this off unless you have been asked to turn it on. "
                                + "It records what causes each stutter and writes new files to the metallum-extra "
                                + "folder in your game directory every time you play. It does not make the game "
                                + "faster. Restart the game after changing it.",
                        Impact.LOW, true, () -> c.profilerEnabled, c::setProfilerEnabled));
    }

    /** Getting other mods to run on Metallum. These are read once at startup. */
    public static List<Toggle> compatibility() {
        ExtraConfig c = ExtraConfig.get();
        return List.of(
                new Toggle("distant_horizons", "Distant Horizons Support",
                        "Lets Distant Horizons run on Metallum instead of crashing. Distant Horizons only knows "
                                + "about OpenGL and Vulkan; this tells it to use its Vulkan-style renderer, which "
                                + "also works on Metal. Experimental. Restart the game after changing it.",
                        Impact.VARIES, true, () -> c.distantHorizonsSupport, c::setDistantHorizonsSupport),
                new Toggle("shine", "Shine Support",
                        "Lets Shine run on Metallum. Shine only knows about OpenGL and Vulkan; this tells it to "
                                + "use its Vulkan-style renderer, which also works on Metal. Experimental. "
                                + "Restart the game after changing it.",
                        Impact.VARIES, true, () -> c.shineSupport, c::setShineSupport));
    }
}
