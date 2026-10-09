package com.metallumextra.shader.pack;

import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * A complete set of shader files. Paths are relative to the pack's {@code shaders/} folder: {@code program/edges.fsh},
 * {@code override/minecraft/core/block.vsh}, {@code lib/lighting.glsl}.
 * <p>
 * A pack never falls back to another one: what it does not contain is missing.
 */
public interface ShaderPack {
    /** The file's text, or null if the pack has no such file. */
    @Nullable String read(String path);

    /** What the player sees in the menu. */
    String name();

    /**
     * The file with every {@code #include "name.glsl"} replaced by {@code lib/name.glsl} from this same pack. A library
     * file goes in once per shader, however many of the files it includes ask for it.
     *
     * @throws IllegalStateException if the file or something it includes is not in the pack
     */
    default String load(final String path) {
        String text = read(path);
        if (text == null) throw new IllegalStateException("Shader pack " + name() + " has no " + path);
        StringBuilder out = new StringBuilder(text.length() + 4096);
        expand(text, path, out, new HashSet<>());
        return out.toString();
    }

    private void expand(final String text, final String file, final StringBuilder out, final Set<String> included) {
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
}
