package com.metallumextra;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Plain-text config at config/metallum-extra.properties.
 * Missing keys fall back to defaults; the file is (re)written with every key so new options show up after updates.
 */
public final class ExtraConfig {
    private static final String FILE_NAME = "metallum-extra.properties";
    private static ExtraConfig instance;

    /** Master switch for the frame-hitch profiler. */
    public volatile boolean profilerEnabled;
    /** A frame only counts as a hitch if it is at least this long... */
    public final double hitchMinMs;
    /** ...and at least this many times longer than the recent average frame. */
    public final double hitchMultiplier;
    /** How often to log a summary line (avg FPS, 1% lows, hitch causes). */
    public final int summarySeconds;
    /** Cap on individual hitch lines in the game log per summary window (all hitches still go to the CSV). */
    public final int maxHitchLogsPerSummary;
    /** Write CPU-visible buffers directly at creation instead of via a GPU copy pass (fix from newer Metallum source). */
    public volatile boolean directBufferUpload;
    /** With vsync off, skip presenting a frame instead of blocking when macOS has no swapchain image free. */
    public volatile boolean nonBlockingPresent;
    /** When the camera enters a new chunk section, update only the section slots that changed (vanilla rescans all). */
    public volatile boolean fastSectionRecenter;
    /** Sodium: empty its freed-buffer queue a little each frame instead of all at once after a GC cycle. */
    public volatile boolean spreadSodiumCleanup;
    /** Distant Horizons: make it use its backend-neutral renderer on Metal. Read once at startup. */
    public volatile boolean distantHorizonsSupport;
    /** Let a render pass draw into several color targets at once (Metallum 0.0.23 supports one). File only; read once at startup. */
    public volatile boolean multipleRenderTargets;
    /** Renumber samplers so shaders with textures in slots 16 and up compile (Metal has 16 sampler slots). File only; read once at startup. */
    public volatile boolean manyTextures;
    /** Shine: make it use its backend-neutral (Vulkan-mode) renderer on Metal. Read once at startup. */
    public volatile boolean shineSupport;

    private Path file;

    private ExtraConfig(final Properties p) {
        this.profilerEnabled = bool(p, "profiler.enabled", false);
        this.hitchMinMs = dbl(p, "profiler.hitchMinMs", 15.0);
        this.hitchMultiplier = dbl(p, "profiler.hitchMultiplier", 4.0);
        this.summarySeconds = Math.max(1, (int) dbl(p, "profiler.summarySeconds", 10));
        this.maxHitchLogsPerSummary = Math.max(0, (int) dbl(p, "profiler.maxHitchLogsPerSummary", 10));
        this.directBufferUpload = bool(p, "fix.directBufferUpload", true);
        this.nonBlockingPresent = bool(p, "fix.nonBlockingPresent", true);
        this.fastSectionRecenter = bool(p, "fix.fastSectionRecenter", true);
        this.spreadSodiumCleanup = bool(p, "fix.spreadSodiumCleanup", true);
        this.distantHorizonsSupport = bool(p, "compat.distantHorizons", true);
        this.multipleRenderTargets = bool(p, "compat.multipleRenderTargets", true);
        this.manyTextures = bool(p, "compat.manyTextures", true);
        this.shineSupport = bool(p, "compat.shine", true);
    }

    /** Live toggles (from the in-game screen): take effect immediately and are written back to the file. */
    public void setDirectBufferUpload(final boolean value) {
        this.directBufferUpload = value;
        save(file);
        MetallumExtra.LOGGER.info("[Metallum Extra] directBufferUpload={}", value);
    }

    public void setFastSectionRecenter(final boolean value) {
        this.fastSectionRecenter = value;
        save(file);
        MetallumExtra.LOGGER.info("[Metallum Extra] fastSectionRecenter={}", value);
    }

    public void setSpreadSodiumCleanup(final boolean value) {
        this.spreadSodiumCleanup = value;
        save(file);
        MetallumExtra.LOGGER.info("[Metallum Extra] spreadSodiumCleanup={}", value);
    }

    public void setDistantHorizonsSupport(final boolean value) {
        this.distantHorizonsSupport = value;
        save(file);
        MetallumExtra.LOGGER.info("[Metallum Extra] distantHorizonsSupport={} (takes effect after a restart)", value);
    }

    public void setShineSupport(final boolean value) {
        this.shineSupport = value;
        save(file);
        MetallumExtra.LOGGER.info("[Metallum Extra] shineSupport={} (takes effect after a restart)", value);
    }

    public void setProfilerEnabled(final boolean value) {
        this.profilerEnabled = value;
        save(file);
        MetallumExtra.LOGGER.info("[Metallum Extra] profilerEnabled={} (takes effect after a restart)", value);
    }

    public void setNonBlockingPresent(final boolean value) {
        this.nonBlockingPresent = value;
        save(file);
        MetallumExtra.LOGGER.info("[Metallum Extra] nonBlockingPresent={}", value);
    }

    public static synchronized ExtraConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static ExtraConfig load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        Properties props = new Properties();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                props.load(r);
            } catch (IOException e) {
                MetallumExtra.LOGGER.warn("[Metallum Extra] Could not read {}, using defaults", file, e);
            }
        }
        ExtraConfig config = new ExtraConfig(props);
        config.file = file;
        config.save(file);
        return config;
    }

    private void save(final Path file) {
        String text = """
                # Metallum Extra configuration. Restart the game after editing this file.
                # The same options are in game: Video Settings > Metallum Extra (with Sodium), or Mods > Metallum Extra
                # (with Mod Menu). The fix.* ones switch live.

                # --- Performance logging (off by default; restart needed) ---
                # For troubleshooting only. Records where every stutter's time went, to the game log
                # and to files in <minecraft folder>/metallum-extra/. Leave off unless you are asked
                # to turn it on: it writes new files every session.
                profiler.enabled=%s
                # A frame is a "hitch" when it is longer than BOTH of these:
                profiler.hitchMinMs=%s
                profiler.hitchMultiplier=%s
                # Seconds between summary lines (avg FPS, 1%% / 0.1%% lows, hitch causes).
                profiler.summarySeconds=%d
                profiler.maxHitchLogsPerSummary=%d

                # --- Fixes ---
                # Fill CPU-visible buffers directly when they are created, instead of
                # breaking the frame for a GPU copy. (Same fix as newer Metallum source.)
                fix.directBufferUpload=%s
                # With vsync off, never stall the render thread waiting for the display. Frames that
                # finish while macOS has no swapchain image free are not shown (the next one is).
                # Has no effect with vsync on.
                fix.nonBlockingPresent=%s
                # Minecraft rescans every section slot in render distance each time you cross into a new
                # chunk section (millions of slots at very high render distances, e.g. with Bobby).
                # This updates only the slots that actually changed.
                fix.fastSectionRecenter=%s
                # Sodium empties a queue of finished chunk-mesh buffers in one go after each garbage
                # collection, which can take 50-130 ms while chunks stream in. This spreads that work
                # over the following frames. No effect without Sodium.
                fix.spreadSodiumCleanup=%s

                # --- Mod compatibility (restart needed) ---
                # Distant Horizons only knows OpenGL and Vulkan and crashes on Metal. This makes it use
                # its Vulkan-style renderer, which is written against the game's own rendering API.
                compat.distantHorizons=%s
                # Shine only knows OpenGL and Vulkan and renders terrain without its own data on Metal.
                # This makes it use its Vulkan-style renderer, which is written against the game's own
                # rendering API.
                compat.shine=%s

                # --- Metallum limits lifted (file only, no in-game switch; restart needed) ---
                # These do nothing unless a mod needs them, so they are always on. They are here only so
                # one can be switched off if it ever causes trouble with some other mod.
                # Metallum can only draw into one image at a time. Mods that draw into several at once
                # (Shine does, for its bloom and rim-light masks) crash with "Render pass created with N
                # color attachments but device only supports 1". This adds support for up to 8.
                compat.multipleRenderTargets=%s
                # Metal has 16 sampler slots per shader and Metallum numbers a sampler after its texture, so
                # a shader whose textures go past number 15 fails to compile ("'sampler' attribute parameter
                # is out of bounds"). This renumbers the samplers of such shaders. Others are untouched.
                compat.manyTextures=%s
                """.formatted(profilerEnabled, hitchMinMs, hitchMultiplier, summarySeconds, maxHitchLogsPerSummary, directBufferUpload, nonBlockingPresent, fastSectionRecenter, spreadSodiumCleanup,
                distantHorizonsSupport, shineSupport, multipleRenderTargets, manyTextures);
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file)) {
                w.write(text);
            }
        } catch (IOException e) {
            MetallumExtra.LOGGER.warn("[Metallum Extra] Could not write {}", file, e);
        }
    }

    private static boolean bool(final Properties p, final String key, final boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    private static double dbl(final Properties p, final String key, final double def) {
        String v = p.getProperty(key);
        if (v == null) {
            return def;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
