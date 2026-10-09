package com.metallumextra.shader.pack;

import com.metallumextra.ExtraConfig;
import com.metallumextra.MetallumExtra;
import com.metallumextra.shader.ShaderSources;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Finds the shader packs in the instance's {@code shaderpacks/} folder and keeps track of which one is in use.
 * <p>
 * Every pack is complete on its own: nothing is ever taken from another pack. A pack is chosen with {@link #select};
 * the change is made at the start of the next frame ({@link #commitPending}), when no shader is being compiled.
 */
public final class PackManager {
    public static final String BUILTIN_ID = "builtin";

    /**
     * The shaders the pipeline asks for, which every pack must contain: this mod's own passes ({@code program/}) and
     * the replacements for the game's and Sodium's shaders ({@code override/}). Anything these {@code #include} comes
     * from {@code lib/} in the same pack.
     */
    public static final List<String> REQUIRED = List.of(
            "program/fullscreen.vsh",
            "program/edges.fsh",
            "program/sky.fsh",
            "program/effects.fsh",
            "program/effects_blur.fsh",
            "program/effects_composite.fsh",
            "program/bloom_prefilter.fsh",
            "program/bloom_downsample.fsh",
            "program/bloom_upsample.fsh",
            "program/bloom_composite.fsh",
            "program/shadow_terrain.vsh",
            "program/shadow_terrain.fsh",
            "override/sodium/blocks/block_layer_opaque.vsh",
            "override/sodium/blocks/block_layer_opaque.fsh",
            "override/minecraft/core/block.vsh",
            "override/minecraft/core/block.fsh",
            "override/minecraft/core/item.vsh",
            "override/minecraft/core/item.fsh",
            "override/minecraft/core/entity.vsh",
            "override/minecraft/core/entity.fsh",
            "override/minecraft/core/particle.vsh",
            "override/minecraft/core/particle.fsh",
            "override/minecraft/core/rendertype_clouds.vsh",
            "override/minecraft/core/rendertype_clouds.fsh");

    /**
     * One line of the menu. {@code pack} is null when the ZIP cannot be used at all; {@code error} is set then, and
     * also for a pack that loaded but failed while the game was using it (it can be tried again).
     * {@code fingerprint} changes when the file does.
     */
    public record Entry(String id, String name, @Nullable ShaderPack pack, @Nullable String error, String fingerprint) {
        public boolean selectable() {
            return pack != null;
        }
    }

    private record Failure(String message, String fingerprint) {
    }

    private static final ShaderPack BUILTIN = new BuiltinPack();
    private static final Entry BUILTIN_ENTRY = new Entry(BUILTIN_ID, BuiltinPack.NAME, BUILTIN, null, "");

    private static final java.util.Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final Map<String, Failure> FAILURES = new ConcurrentHashMap<>();

    private static volatile List<Entry> entries = List.of(BUILTIN_ENTRY);
    private static volatile ShaderPack active = BUILTIN;
    private static volatile String activeId = BUILTIN_ID;
    private static volatile String activeFingerprint = "";
    private static volatile @Nullable Entry pending;
    private static volatile boolean flush;
    private static volatile @Nullable String problem;
    private static boolean started;

    /** The pack to fall back to if the active one breaks: the one in use before it, if that worked. */
    private static ShaderPack lastWorking = BUILTIN;
    private static String lastWorkingId = BUILTIN_ID;
    private static String lastWorkingFingerprint = "";

    private PackManager() {
    }

    // ---- reading the folder ----

    /** The folder the packs are read from, in this game instance. */
    public static Path folder() {
        return FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
    }

    /**
     * What a folder holds: the built-in pack, then every ZIP in it that has a {@code pack.json}, in name order.
     * ZIPs without one (shader packs for other mods) are not ours and are left out. A ZIP that has one but cannot be
     * used is listed with the reason. Two ZIPs that are the same pack by name are both refused.
     */
    public static List<Entry> scan(final Path folder) {
        List<Path> zips = new ArrayList<>();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> files = Files.list(folder)) {
                files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip") && Files.isRegularFile(p))
                        .sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                        .forEach(zips::add);
            } catch (IOException e) {
                MetallumExtra.LOGGER.warn("[Metallum Extra] Could not read {}", folder, e);
            }
        }

        Map<String, Integer> byName = new HashMap<>();
        byName.put(BuiltinPack.NAME.toLowerCase(Locale.ROOT), 1);
        List<Path> packs = new ArrayList<>();
        for (Path zip : zips) {
            if (!ZipPack.isPack(zip)) continue;
            packs.add(zip);
            byName.merge(stem(zip).toLowerCase(Locale.ROOT), 1, Integer::sum);
        }

        List<Entry> result = new ArrayList<>();
        result.add(BUILTIN_ENTRY);
        for (Path zip : packs) {
            String id = zip.getFileName().toString();
            String name = stem(zip);
            String fingerprint = fingerprint(zip);
            if (byName.get(name.toLowerCase(Locale.ROOT)) > 1) {
                String other = name.equalsIgnoreCase(BuiltinPack.NAME) ? "the built-in pack" : "another ZIP";
                result.add(new Entry(id, name, null, "Has the same name as " + other + ", so it is not used. Rename it.", fingerprint));
                continue;
            }
            try {
                ZipPack pack = ZipPack.open(zip);
                validate(pack);
                Failure failure = FAILURES.get(id);
                String error = failure != null && failure.fingerprint.equals(fingerprint) ? failure.message : null;
                result.add(new Entry(id, name, pack, error, fingerprint));
            } catch (PackException e) {
                // Said once per version of the file, not every time the menu opens.
                if (WARNED.add(id + ":" + fingerprint)) {
                    MetallumExtra.LOGGER.warn("[Metallum Extra] Shader pack {} is not usable: {}", id, e.getMessage());
                }
                result.add(new Entry(id, name, null, e.getMessage(), fingerprint));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Checks that the pack holds every required shader and that everything they include is in the pack too.
     *
     * @throws PackException naming what is missing
     */
    public static void validate(final ShaderPack pack) throws PackException {
        List<String> missing = new ArrayList<>();
        for (String path : REQUIRED) {
            if (pack.read(path) == null) missing.add(path);
        }
        if (!missing.isEmpty()) {
            String shown = String.join(", ", missing.subList(0, Math.min(3, missing.size())));
            throw new PackException("Missing " + shown + (missing.size() > 3 ? " and " + (missing.size() - 3) + " more" : ""));
        }
        for (String path : REQUIRED) {
            try {
                pack.load(path);
            } catch (IllegalStateException e) {
                throw new PackException(e.getMessage(), e);
            }
        }
    }

    private static String stem(final Path zip) {
        String file = zip.getFileName().toString();
        return file.substring(0, file.length() - ".zip".length());
    }

    private static String fingerprint(final Path zip) {
        try {
            return Files.size(zip) + ":" + Files.getLastModifiedTime(zip).toMillis();
        } catch (IOException e) {
            return "?";
        }
    }

    // ---- state ----

    /** Reads the folder and picks the pack saved in the config. Called once, before the first shader is asked for. */
    public static synchronized void start() {
        if (started) return;
        started = true;
        entries = scan(folder());
        String saved = ExtraConfig.get().shaderPack;
        Entry entry = find(saved);
        if (entry != null && entry.selectable()) {
            apply(entry);
            if (entry.error != null) problem = entry.error;
        } else if (!saved.equals(BUILTIN_ID)) {
            String why = entry == null ? "is not in the shaderpacks folder" : "cannot be used: " + entry.error;
            problem = "Shader pack " + saved + " " + why + ". Using the built-in shaders.";
            MetallumExtra.LOGGER.warn("[Metallum Extra] {}", problem);
        }
    }

    /** Scans the folder again; the menu calls this when it opens, which is why it needs no reload button. */
    public static synchronized void rescan() {
        start();
        entries = scan(folder());
    }

    public static List<Entry> entries() {
        start();
        return entries;
    }

    public static ShaderPack active() {
        start();
        return active;
    }

    public static String activeId() {
        start();
        return pending != null ? pending.id : activeId;
    }

    /** A sentence about something that went wrong on startup, for the menu; null if nothing did. */
    public static @Nullable String problem() {
        return problem;
    }

    public static @Nullable Entry find(final String id) {
        for (Entry entry : entries) {
            if (entry.id.equals(id)) return entry;
        }
        return null;
    }

    /**
     * Asks for a pack to be used from the next frame on, and remembers the choice in the config. Choosing the pack
     * already in use does nothing unless its file has changed, in which case it is read in again.
     *
     * @return why the pack cannot be used; null if it will be
     */
    public static synchronized @Nullable String select(final String id) {
        start();
        Entry entry = find(id);
        if (entry == null) return "No such shader pack: " + id;
        if (!entry.selectable()) return entry.error;
        ExtraConfig.get().setShaderPack(id);
        if (id.equals(activeId) && entry.fingerprint.equals(activeFingerprint) && !FAILURES.containsKey(id)) {
            pending = null;
        } else {
            pending = entry;
        }
        return null;
    }

    /**
     * Start of a frame: puts a pack chosen since the last frame into use.
     *
     * @return whether the pack in use changed, so compiled pipelines must be thrown away
     */
    public static synchronized boolean commitPending() {
        boolean changed = flush;
        flush = false;
        Entry entry = pending;
        if (entry == null) return changed;
        pending = null;
        if (!FAILURES.containsKey(activeId)) {
            lastWorking = active;
            lastWorkingId = activeId;
            lastWorkingFingerprint = activeFingerprint;
        }
        FAILURES.remove(entry.id);
        apply(entry);
        MetallumExtra.LOGGER.info("[Metallum Extra] Shader pack: {}", entry.name);
        return true;
    }

    private static void apply(final Entry entry) {
        ShaderPack pack = entry.pack;
        active = pack;
        activeId = entry.id;
        activeFingerprint = entry.fingerprint;
        ShaderSources.use(pack);
    }

    /**
     * A shader of the pack in use did not compile, or a file it needs is missing. The pack is put aside, with the
     * reason shown in the menu, and the one in use before it (or the built-in shaders) takes over right away; the
     * pipelines built so far are thrown away at the start of the next frame.
     *
     * @return false if the built-in shaders were in use, which have nothing to fall back to
     */
    public static synchronized boolean fail(final Throwable cause) {
        if (activeId.equals(BUILTIN_ID)) return false;
        String failedId = activeId;
        String message = describe(cause);
        FAILURES.put(failedId, new Failure(message, activeFingerprint));
        MetallumExtra.LOGGER.error("[Metallum Extra] Shader pack {} is broken and is switched off: {}", failedId, message, cause);

        boolean usable = !lastWorkingId.equals(failedId) && !FAILURES.containsKey(lastWorkingId);
        Entry fallback = usable
                ? new Entry(lastWorkingId, lastWorkingId, lastWorking, null, lastWorkingFingerprint)
                : BUILTIN_ENTRY;
        apply(fallback);
        pending = null;
        flush = true;
        problem = "Shader pack " + failedId + " failed: " + message;
        ExtraConfig.get().setShaderPack(fallback.id);
        entries = markFailed(entries, failedId, message);
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> SystemToast.add(minecraft.gui.toastManager(), SystemToast.SystemToastId.PACK_LOAD_FAILURE,
                Component.literal("Shader pack " + failedId + " failed"),
                Component.literal(usable ? "Back to " + lastWorkingId : "Back to the built-in shaders")));
        return true;
    }

    private static List<Entry> markFailed(final List<Entry> list, final String id, final String message) {
        List<Entry> result = new ArrayList<>(list.size());
        for (Entry entry : list) {
            result.add(entry.id.equals(id) ? new Entry(entry.id, entry.name, entry.pack, message, entry.fingerprint) : entry);
        }
        return List.copyOf(result);
    }

    /** The messages of an exception and what caused it, short enough for a menu line. */
    public static String describe(final Throwable error) {
        StringBuilder text = new StringBuilder();
        for (Throwable e = error; e != null; e = e.getCause() == e ? null : e.getCause()) {
            String message = e.getMessage();
            if (message == null || message.isBlank() || text.indexOf(message) >= 0) continue;
            if (!text.isEmpty()) text.append(" - ");
            text.append(message.strip());
        }
        String line = text.isEmpty() ? error.getClass().getSimpleName() : text.toString().replaceAll("\\s*\\R\\s*", " | ");
        return line.length() > 400 ? line.substring(0, 400) + "..." : line;
    }
}
