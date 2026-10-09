package com.metallumextra.shader.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * A shader pack in an ordinary ZIP file: {@code pack.json} and a {@code shaders/} folder. The whole folder is read
 * into memory when the pack is opened, so the pack cannot change under a shader that is being compiled, and the file
 * can be replaced on disk while it is in use.
 */
public final class ZipPack implements ShaderPack {
    public static final int FORMAT = 1;
    private static final String SHADERS = "shaders/";
    /** A pack is a few hundred kilobytes of text; this only stops a damaged or hostile file from filling memory. */
    private static final long MAX_BYTES = 64L * 1024 * 1024;

    private final String name;
    private final Map<String, String> files;

    private ZipPack(final String name, final Map<String, String> files) {
        this.name = name;
        this.files = files;
    }

    /** Whether the ZIP declares itself a shader pack at all (it has a {@code pack.json}). Other ZIPs are not ours to judge. */
    public static boolean isPack(final Path zip) {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            return file.getEntry("pack.json") != null;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** @throws PackException if the ZIP cannot be read or its {@code pack.json} is not one this mod understands */
    public static ZipPack open(final Path zip) throws PackException {
        String fileName = zip.getFileName().toString();
        String name = fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".zip") ? fileName.substring(0, fileName.length() - 4) : fileName;
        try (ZipFile file = new ZipFile(zip.toFile())) {
            ZipEntry meta = file.getEntry("pack.json");
            if (meta == null) throw new PackException("pack.json is missing");
            checkFormat(new String(read(file, meta, MAX_BYTES), StandardCharsets.UTF_8));

            Map<String, String> files = new HashMap<>();
            long budget = MAX_BYTES;
            for (var entries = file.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entry.isDirectory() || !entryName.startsWith(SHADERS)) continue;
                byte[] bytes = read(file, entry, budget);
                budget -= bytes.length;
                files.put(entryName.substring(SHADERS.length()), new String(bytes, StandardCharsets.UTF_8));
            }
            return new ZipPack(name, Map.copyOf(files));
        } catch (IOException | RuntimeException e) {
            if (e instanceof PackException pack) throw pack;
            throw new PackException("could not read the ZIP: " + e.getMessage(), e);
        }
    }

    private static void checkFormat(final String json) throws PackException {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new PackException("pack.json is not valid JSON", e);
        }
        if (!root.has("format") || !root.get("format").isJsonPrimitive() || !root.get("format").getAsJsonPrimitive().isNumber()) {
            throw new PackException("pack.json has no \"format\" number");
        }
        int format = root.get("format").getAsInt();
        if (format != FORMAT) {
            throw new PackException("pack format " + format + " is not supported (this version reads format " + FORMAT + ")");
        }
    }

    private static byte[] read(final ZipFile file, final ZipEntry entry, final long limit) throws IOException {
        try (InputStream in = file.getInputStream(entry)) {
            byte[] bytes = in.readNBytes((int) Math.min(limit + 1, Integer.MAX_VALUE));
            if (bytes.length > limit) throw new IOException(entry.getName() + " is too large");
            return bytes;
        }
    }

    @Override
    public @Nullable String read(final String path) {
        return files.get(path);
    }

    @Override
    public String name() {
        return name;
    }
}
