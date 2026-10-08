package com.metallumextra.shader;

import com.metallumextra.MetallumExtra;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Glow around the brightest parts of the finished world image.
 * <p>
 * The image is taken at half size with its light as bright as it was before being rolled off towards white,
 * shrunk four more times, then grown back level by level with each level added to the one above it, which gives
 * a blur that is tight near a bright thing and wide further out. A thin layer of that is laid over the world
 * image, so brightness spills from the sun, lava and torches onto what is next to them.
 */
public final class BloomPass {
    private static final BindGroupLayout INPUT = BindGroupLayout.builder().withSampler("InSampler").build();
    private static final ColorTargetState TO_BLOOM = new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL);
    private static final ColorTargetState ADD_TO_BLOOM = new ColorTargetState(Optional.of(BlendFunction.ADDITIVE), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL);
    /** A layer: result = glow + world * (1 - share), the shader having scaled the glow by its share. The world image's alpha is left alone. */
    private static final ColorTargetState LAYER_ONTO_WORLD = new ColorTargetState(
            Optional.of(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.ZERO, BlendFactor.ONE)),
            GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR);

    private static final RenderPipeline PREFILTER = pipeline("bloom_prefilter", TO_BLOOM);
    private static final RenderPipeline DOWNSAMPLE = pipeline("bloom_downsample", TO_BLOOM);
    private static final RenderPipeline UPSAMPLE = pipeline("bloom_upsample", ADD_TO_BLOOM);
    private static final RenderPipeline COMPOSITE = pipeline("bloom_composite", LAYER_ONTO_WORLD);

    private BloomPass() {
    }

    private static RenderPipeline pipeline(final String fragment, final ColorTargetState target) {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(MetallumExtra.MOD_ID, "pipeline/" + fragment))
                .withVertexShader(Identifier.fromNamespaceAndPath(MetallumExtra.MOD_ID, "fullscreen"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(MetallumExtra.MOD_ID, fragment))
                .withBindGroupLayout(INPUT)
                .withColorTargetState(target)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build();
    }

    public static void render(final RenderTarget main) {
        ShaderTargets targets = Shaders.targets();
        if (targets.scene() == null || main.width != targets.width() || main.height != targets.height()) return;

        draw(PREFILTER, main.getColorTextureView(), targets.bloom(0).getColorTextureView());
        for (int level = 1; level < ShaderTargets.BLOOM_LEVELS; level++) {
            draw(DOWNSAMPLE, targets.bloom(level - 1).getColorTextureView(), targets.bloom(level).getColorTextureView());
        }
        for (int level = ShaderTargets.BLOOM_LEVELS - 1; level > 0; level--) {
            draw(UPSAMPLE, targets.bloom(level).getColorTextureView(), targets.bloom(level - 1).getColorTextureView());
        }
        draw(COMPOSITE, targets.bloom(0).getColorTextureView(), main.getColorTextureView());
    }

    private static void draw(final RenderPipeline pipeline, final GpuTextureView input, final GpuTextureView output) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (RenderPass pass = encoder.createRenderPass(() -> "Metallum Extra bloom", output, Optional.empty())) {
            pass.setPipeline(pipeline);
            pass.bindTexture("InSampler", input, linear);
            pass.draw(3, 1, 0, 0);
        }
    }
}
