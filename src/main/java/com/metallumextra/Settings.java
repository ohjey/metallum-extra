package com.metallumextra;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** The player-facing settings, defined once and shown by both the Sodium video settings page and the Mod Menu screen. */
public final class Settings {
    public enum Impact { LOW, MEDIUM, HIGH, VARIES }

    public record Toggle(String id, String name, String tooltip, Impact impact, boolean needsRestart,
                         BooleanSupplier getter, Consumer<Boolean> setter) {
    }

    private Settings() {
    }

    /** Stutter and frame-rate fixes. All of these switch on and off immediately. */
    public static List<Toggle> smoothness() {
        ExtraConfig c = ExtraConfig.get();
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
                        "Sends small pieces of render data straight to memory instead of through an extra GPU "
                                + "step. A small improvement; safe to leave on.",
                        Impact.LOW, false, () -> c.directBufferUpload, c::setDirectBufferUpload));
    }

    /** Getting other mods to run on Metallum. These are read once at startup. */
    public static List<Toggle> compatibility() {
        ExtraConfig c = ExtraConfig.get();
        return List.of(
                new Toggle("distant_horizons", "Distant Horizons Support",
                        "Lets Distant Horizons run on Metallum instead of crashing. Distant Horizons only knows "
                                + "about OpenGL and Vulkan; this tells it to use its Vulkan-style renderer, which "
                                + "also works on Metal. Experimental. Restart the game after changing it.",
                        Impact.VARIES, true, () -> c.distantHorizonsSupport, c::setDistantHorizonsSupport));
    }
}
