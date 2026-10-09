package com.metallumextra.shader.pack;

import com.metallumextra.MetallumExtra;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The shaders inside this mod's jar. */
public final class BuiltinPack implements ShaderPack {
    public static final String NAME = "Metallically Beautiful";

    private static final String ROOT = "/assets/metallum-extra/shaders/";
    /** Development aid: read the shaders from this folder instead of the jar, so they can be edited while the game runs. */
    private static final @Nullable Path DEV_DIR = devDir();

    @Override
    public @Nullable String read(final String path) {
        try {
            if (DEV_DIR != null) {
                Path file = DEV_DIR.resolve(path);
                return Files.isRegularFile(file) ? Files.readString(file) : null;
            }
            try (InputStream in = BuiltinPack.class.getResourceAsStream(ROOT + path)) {
                return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            MetallumExtra.LOGGER.error("[Metallum Extra] Could not read shader {}", path, e);
            return null;
        }
    }

    @Override
    public String name() {
        return NAME;
    }

    private static @Nullable Path devDir() {
        String dir = System.getProperty("metallumextra.shaderDir");
        return dir == null || dir.isBlank() ? null : Path.of(dir);
    }
}
