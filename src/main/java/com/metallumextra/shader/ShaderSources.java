package com.metallumextra.shader;

import com.metallumextra.MetallumExtra;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The GLSL this mod ships, looked up by the same id the game or Sodium asks its own shaders by.
 * <p>
 * Two kinds of file live under {@code assets/metallum-extra/shaders/}:
 * <ul>
 * <li>{@code override/<namespace>/<path>.vsh|.fsh} replaces the shader another mod or the game registered under
 * {@code <namespace>:<path>} while shaders are on. The pipeline stays theirs; only its text changes.</li>
 * <li>{@code program/<path>.vsh|.fsh} is a shader of this mod's own pipelines, asked for as {@code metallum-extra:<path>}.</li>
 * </ul>
 * A line {@code #include "name.glsl"} is replaced by {@code lib/name.glsl} the first time a shader asks for
 * that file, and dropped after that, so library files can include what they need. Every file must start with its
 * {@code #version} line, since the game inserts a pipeline's defines right after the first line.
 */
public final class ShaderSources {
    private static final String ROOT = "/assets/metallum-extra/shaders/";
    /** Development aid: read the shaders from this folder instead of the jar, so they can be edited while the game runs. */
    private static final @Nullable Path DEV_DIR = devDir();

    /** Development aid: every shader the game compiles (as other mods left it) is written to this folder. */
    private static final @Nullable Path DUMP_DIR = dir("metallumextra.dumpShaders");

    private static final Map<String, Optional<String>> CACHE = new ConcurrentHashMap<>();

    private ShaderSources() {
    }

    /** Wraps the source the game compiles from, so this mod's shaders are found first while shaders are on. */
    public static ShaderSource wrap(final ShaderSource original) {
        return (id, type) -> {
            String ours = Shaders.active() ? get(id, type) : null;
            if (DUMP_DIR != null) dump(id, type, original.get(id, type));
            return ours != null ? ours : original.get(id, type);
        };
    }

    public static @Nullable String get(final Identifier id, final ShaderType type) {
        String extension = type == ShaderType.VERTEX ? ".vsh" : ".fsh";
        String file = id.getNamespace().equals(MetallumExtra.MOD_ID)
                ? "program/" + id.getPath() + extension
                : "override/" + id.getNamespace() + "/" + id.getPath() + extension;
        return CACHE.computeIfAbsent(file, ShaderSources::load).orElse(null);
    }

    public static void clear() {
        CACHE.clear();
    }

    private static Optional<String> load(final String file) {
        String text = read(file);
        if (text == null) return Optional.empty();
        StringBuilder out = new StringBuilder(text.length() + 4096);
        expand(text, file, out, new HashSet<>());
        return Optional.of(out.toString());
    }

    /** Each library file goes in once per shader, however many of the files it includes ask for it. */
    private static void expand(final String text, final String file, final StringBuilder out, final Set<String> included) {
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include")) {
                int open = trimmed.indexOf('"');
                int close = trimmed.lastIndexOf('"');
                if (open < 0 || close <= open) throw new IllegalStateException("Bad #include in " + file + ": " + line);
                String name = "lib/" + trimmed.substring(open + 1, close);
                if (!included.add(name)) continue;
                String library = read(name);
                if (library == null) throw new IllegalStateException("Missing shader include " + name + " (from " + file + ")");
                expand(library, name, out, included);
            } else {
                out.append(line).append('\n');
            }
        }
    }

    private static @Nullable String read(final String file) {
        try {
            if (DEV_DIR != null) {
                Path path = DEV_DIR.resolve(file);
                return Files.isRegularFile(path) ? Files.readString(path) : null;
            }
            try (InputStream in = ShaderSources.class.getResourceAsStream(ROOT + file)) {
                return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            MetallumExtra.LOGGER.error("[Metallum Extra] Could not read shader {}", file, e);
            return null;
        }
    }

    private static void dump(final Identifier id, final ShaderType type, final @Nullable String text) {
        if (text == null) return;
        try {
            Path file = DUMP_DIR.resolve(id.getNamespace() + "." + id.getPath().replace('/', '.') + (type == ShaderType.VERTEX ? ".vsh" : ".fsh"));
            if (!Files.exists(file)) {
                Files.createDirectories(DUMP_DIR);
                Files.writeString(file, text);
            }
        } catch (IOException e) {
            MetallumExtra.LOGGER.warn("[Metallum Extra] Could not dump shader {}", id, e);
        }
    }

    private static @Nullable Path dir(final String property) {
        String dir = System.getProperty(property);
        return dir == null || dir.isBlank() ? null : Path.of(dir);
    }

    private static @Nullable Path devDir() {
        return dir("metallumextra.shaderDir");
    }
}
