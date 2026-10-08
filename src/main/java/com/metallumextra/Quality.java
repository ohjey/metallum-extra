package com.metallumextra;

/**
 * Shader quality presets. A preset is a set of values for the individual shader settings; picking one writes
 * them all, and the individual settings stay free to change afterwards, at which point the preset reads as
 * Custom. Medium is the default: shadows and reflections, but no sun rays and a shorter shadow distance.
 */
public enum Quality {
    LOW("Low", 1024, 4, false, false, false),
    MEDIUM("Medium", 2048, 6, true, false, true),
    HIGH("High", 2048, 8, true, true, true),
    ULTRA("Ultra", 4096, 12, true, true, true),
    CUSTOM("Custom", 0, 0, false, false, false);

    public final String label;
    final int shadowResolution;
    final int shadowDistance;
    final boolean reflections;
    final boolean sunRays;
    final boolean ambientOcclusion;

    Quality(final String label, final int shadowResolution, final int shadowDistance, final boolean reflections, final boolean sunRays, final boolean ambientOcclusion) {
        this.label = label;
        this.shadowResolution = shadowResolution;
        this.shadowDistance = shadowDistance;
        this.reflections = reflections;
        this.sunRays = sunRays;
        this.ambientOcclusion = ambientOcclusion;
    }

    /** The preset these settings are, or Custom if they match none. */
    static Quality of(final ExtraConfig c) {
        for (Quality quality : values()) {
            if (quality == CUSTOM) continue;
            if (c.shaderShadows && c.shadowResolution == quality.shadowResolution && c.shadowDistance == quality.shadowDistance
                    && c.shaderWaterReflections == quality.reflections && c.shaderSunRays == quality.sunRays
                    && c.shaderAmbientOcclusion == quality.ambientOcclusion && c.shaderBloom && c.shaderWaving && c.shaderColoredLight) {
                return quality;
            }
        }
        return CUSTOM;
    }
}
