package com.metallumextra.shader;

import com.metallumextra.ExtraConfig;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jspecify.annotations.Nullable;

/** The images the shader pipeline owns, sized after the game's own world image. */
public final class ShaderTargets {
    /** Half size down to 1/32: wide enough for a soft glow without the smallest level shimmering. */
    public static final int BLOOM_LEVELS = 5;

    private int width = 1;
    private int height = 1;
    private int shadowResolution = 1;

    /** A copy of the world's color and depth taken before translucent terrain, for water to reflect and refract. */
    private @Nullable TextureTarget scene;
    private final TextureTarget[] bloom = new TextureTarget[BLOOM_LEVELS];
    private @Nullable TextureTarget shadow;
    /** One empty texel, bound in place of the shadow map while the shadow map is the image being drawn into. */
    private @Nullable TextureTarget emptyShadow;
    /** Half-size images for the screen effects (sun rays, ambient occlusion): one drawn, one blurred. */
    private @Nullable TextureTarget effects;
    private @Nullable TextureTarget effectsBlurred;

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public int shadowResolution() {
        return this.shadowResolution;
    }

    public TextureTarget scene() {
        return this.scene;
    }

    public TextureTarget bloom(final int level) {
        return this.bloom[level];
    }

    public TextureTarget shadow() {
        return this.shadow;
    }

    public TextureTarget emptyShadow() {
        return this.emptyShadow;
    }

    public TextureTarget effects() {
        return this.effects;
    }

    public TextureTarget effectsBlurred() {
        return this.effectsBlurred;
    }

    /** Creates or resizes everything to match the main image. Called at the start of each frame while shaders are on. */
    public void prepare(final RenderTarget main) {
        if (this.scene == null || main.width != this.width || main.height != this.height) {
            this.width = Math.max(main.width, 1);
            this.height = Math.max(main.height, 1);
            if (this.scene != null) this.scene.destroyBuffers();
            this.scene = new TextureTarget("Metallum Extra scene copy", this.width, this.height, true, GpuFormat.RGBA8_UNORM);
            for (int level = 0; level < BLOOM_LEVELS; level++) {
                if (this.bloom[level] != null) this.bloom[level].destroyBuffers();
                int levelWidth = Math.max(this.width >> (level + 1), 1);
                int levelHeight = Math.max(this.height >> (level + 1), 1);
                this.bloom[level] = new TextureTarget("Metallum Extra bloom " + level, levelWidth, levelHeight, false, GpuFormat.RGBA16_FLOAT);
            }
            if (this.effects != null) this.effects.destroyBuffers();
            if (this.effectsBlurred != null) this.effectsBlurred.destroyBuffers();
            int halfWidth = Math.max(this.width / 2, 1);
            int halfHeight = Math.max(this.height / 2, 1);
            this.effects = new TextureTarget("Metallum Extra effects", halfWidth, halfHeight, false, GpuFormat.RGBA16_FLOAT);
            this.effectsBlurred = new TextureTarget("Metallum Extra effects blurred", halfWidth, halfHeight, false, GpuFormat.RGBA16_FLOAT);
        }

        if (this.emptyShadow == null) {
            this.emptyShadow = new TextureTarget("Metallum Extra empty shadow map", 1, 1, true, GpuFormat.RGBA8_UNORM);
            ShadowPass.clear(this.emptyShadow);
        }

        // With shadows off the map still has to exist, since the shaders declare it; one texel is enough.
        int wantedResolution = ExtraConfig.get().shaderShadows ? ExtraConfig.get().shadowResolution : 1;
        if (this.shadow == null || wantedResolution != this.shadowResolution) {
            if (this.shadow != null) this.shadow.destroyBuffers();
            this.shadowResolution = wantedResolution;
            this.shadow = new TextureTarget("Metallum Extra shadow map", wantedResolution, wantedResolution, true, GpuFormat.RGBA8_UNORM);
            ShadowPass.clear(this.shadow);
        }
    }

    public void copyScene(final RenderTarget main) {
        if (this.scene == null || main.width != this.width || main.height != this.height) return;
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToTexture(main.getColorTexture(), this.scene.getColorTexture(), 0, 0, 0, 0, 0, this.width, this.height);
        encoder.copyTextureToTexture(main.getDepthTexture(), this.scene.getDepthTexture(), 0, 0, 0, 0, 0, this.width, this.height);
    }

    public void close() {
        if (this.scene != null) this.scene.destroyBuffers();
        this.scene = null;
        for (int level = 0; level < BLOOM_LEVELS; level++) {
            if (this.bloom[level] != null) this.bloom[level].destroyBuffers();
            this.bloom[level] = null;
        }
        if (this.effects != null) this.effects.destroyBuffers();
        this.effects = null;
        if (this.effectsBlurred != null) this.effectsBlurred.destroyBuffers();
        this.effectsBlurred = null;
        if (this.shadow != null) this.shadow.destroyBuffers();
        this.shadow = null;
        if (this.emptyShadow != null) this.emptyShadow.destroyBuffers();
        this.emptyShadow = null;
        this.width = 1;
        this.height = 1;
        this.shadowResolution = 1;
    }
}
