package com.metallumextra.shader.pack;

import com.metallumextra.MetallumExtra;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metal shader text (MSL) that SPIRV-Cross made from a shader's SPIR-V, kept on disk so it is not made again.
 * <p>
 * The name of an entry is a SHA-256 of everything that can change the text: the SPIR-V itself (which holds the
 * shader's stage and, after Metallum has numbered them, its resource bindings), the vertex input formats, the
 * options the conversion is run with, and the versions of the programs involved. It does not depend on which pack the
 * shader came from, so two packs with the same shader share an entry. An entry holds what Metallum needs besides the
 * text: whether the shader has push constants and which resources it uses.
 * <p>
 * Metal pipelines are not kept here. They belong to the running GPU device and render state, and are rebuilt from
 * this text (see {@code MetalDevice.clearPipelineCache}).
 */
public final class TranslationCache {
    /** Bump when the file layout changes. */
    private static final String FORMAT = "1";
    private static final String MAGIC = "// metallum-extra translation cache " + FORMAT;
    private static final String SEPARATOR = "\n// ---\n";

    /** What a conversion produces; the same three parts as Metallum's own result. */
    public record Msl(String source, boolean hasPushConstants, Set<String> activeResources) {
    }

    private static final AtomicInteger HITS = new AtomicInteger();
    private static final AtomicInteger TRANSLATIONS = new AtomicInteger();
    private static final AtomicLong LAST_CHANGE = new AtomicLong();
    private static int loggedHits;
    private static int loggedTranslations;

    private static volatile @Nullable TranslationCache shared;

    private final Path folder;
    private boolean warned;

    public TranslationCache(final Path folder) {
        this.folder = folder;
    }

    /** The cache in this game instance's {@code cache/metallum-extra/shaders/}. */
    public static TranslationCache shared() {
        TranslationCache cache = shared;
        if (cache == null) {
            cache = new TranslationCache(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("cache/metallum-extra/shaders"));
            shared = cache;
        }
        return cache;
    }

    /**
     * The name of the entry for one conversion.
     *
     * @param salt             the versions and settings that apply to every conversion; see {@code MetallumExtraBridge}
     * @param spirv            the shader as SPIR-V, from its current position
     * @param pushConstantBinding where Metallum puts the push constants
     * @param attributeFormats vertex input name to its format, for the vertex stage
     * @param fragDepth        whether the fragment depth output is kept
     */
    public static String key(final String salt, final ByteBuffer spirv, final int pushConstantBinding,
                             final Map<String, ?> attributeFormats, final boolean fragDepth) {
        MessageDigest digest = sha256();
        update(digest, salt);
        update(digest, Integer.toString(pushConstantBinding));
        update(digest, Boolean.toString(fragDepth));
        for (Map.Entry<String, ?> entry : new TreeMap<>(attributeFormats).entrySet()) {
            update(digest, entry.getKey() + "=" + entry.getValue());
        }
        update(digest, "spirv");
        digest.update(spirv.duplicate());
        return HexFormat.of().formatHex(digest.digest());
    }

    /** The entry for a key, or null if there is none or it is damaged (a damaged one is removed). */
    public @Nullable Msl lookup(final String key) {
        Path file = folder.resolve(key + ".msl");
        if (!Files.isRegularFile(file)) return null;
        try {
            Msl msl = parse(Files.readString(file, StandardCharsets.UTF_8));
            if (msl != null) {
                HITS.incrementAndGet();
                LAST_CHANGE.set(System.nanoTime());
                return msl;
            }
        } catch (IOException | RuntimeException e) {
            // Treated as damaged, below.
        }
        MetallumExtra.LOGGER.warn("[Metallum Extra] Ignoring damaged shader cache entry {}", file.getFileName());
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
        return null;
    }

    /** Counts a conversion that had to be run, and keeps its result. A cache that cannot be written to does no harm. */
    public void store(final String key, final Msl msl) {
        TRANSLATIONS.incrementAndGet();
        LAST_CHANGE.set(System.nanoTime());
        Path target = folder.resolve(key + ".msl");
        Path temporary = folder.resolve(key + "." + Long.toHexString(System.nanoTime()) + ".tmp");
        try {
            Files.createDirectories(folder);
            Files.writeString(temporary, format(msl), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
            if (!warned) {
                warned = true;
                MetallumExtra.LOGGER.warn("[Metallum Extra] Could not write the shader cache in {}", folder, e);
            }
        }
    }

    static String format(final Msl msl) {
        StringBuilder out = new StringBuilder(msl.source().length() + 256);
        out.append(MAGIC).append('\n');
        out.append("// push-constants: ").append(msl.hasPushConstants()).append('\n');
        for (String resource : new TreeSet<>(msl.activeResources())) {
            out.append("// resource: ").append(resource).append('\n');
        }
        out.append("// sha256: ").append(hash(msl.source()));
        out.append(SEPARATOR).append(msl.source());
        return out.toString();
    }

    /** @return null if the text is not a complete entry of this format */
    static @Nullable Msl parse(final String text) {
        int split = text.indexOf(SEPARATOR);
        if (split < 0) return null;
        String[] header = text.substring(0, split).split("\n");
        if (header.length < 3 || !header[0].equals(MAGIC)) return null;
        String source = text.substring(split + SEPARATOR.length());

        Boolean push = null;
        String checksum = null;
        Set<String> resources = new TreeSet<>();
        for (int i = 1; i < header.length; i++) {
            String line = header[i];
            if (line.startsWith("// push-constants: ")) push = Boolean.parseBoolean(line.substring("// push-constants: ".length()));
            else if (line.startsWith("// resource: ")) resources.add(line.substring("// resource: ".length()));
            else if (line.startsWith("// sha256: ")) checksum = line.substring("// sha256: ".length());
        }
        if (push == null || checksum == null || !checksum.equals(hash(source))) return null;
        return new Msl(source, push, resources);
    }

    // ---- the debug counter ----

    public static int hits() {
        return HITS.get();
    }

    public static int translations() {
        return TRANSLATIONS.get();
    }

    public static String summary() {
        return "MSL cache hits: " + HITS.get() + ", MSL translations: " + TRANSLATIONS.get();
    }

    /** Called every frame: once the counters have been still for a moment, says what they came to. */
    public static void logIfSettled() {
        int hits = HITS.get();
        int translations = TRANSLATIONS.get();
        if ((hits == loggedHits && translations == loggedTranslations) || System.nanoTime() - LAST_CHANGE.get() < 1_500_000_000L) return;
        loggedHits = hits;
        loggedTranslations = translations;
        MetallumExtra.LOGGER.info("[Metallum Extra] {}", summary());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void update(final MessageDigest digest, final String text) {
        digest.update(text.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String hash(final String text) {
        return HexFormat.of().formatHex(sha256().digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
