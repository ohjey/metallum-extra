package com.metallumextra;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

/** Which Metallum release is installed, for the few places where the releases this mod supports differ. */
public final class MetallumVersion {
    /**
     * Metallum 0.0.24 writes a new buffer's data straight into CPU-visible memory itself, which is what
     * {@code fix.directBufferUpload} does for 0.0.23.
     */
    public static final boolean WRITES_BUFFERS_DIRECTLY = atLeast("0.0.24");

    private MetallumVersion() {
    }

    private static boolean atLeast(final String version) {
        Version installed = FabricLoader.getInstance().getModContainer("metallum")
                .map(container -> container.getMetadata().getVersion())
                .orElse(null);
        if (installed == null) return false;
        try {
            return installed.compareTo(Version.parse(version)) >= 0;
        } catch (VersionParsingException e) {
            throw new IllegalArgumentException(version, e);
        }
    }
}
