package com.metallumextra.shader;

import com.metallumextra.MetallumExtra;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Screen effects worked out from the finished world's depth: sun rays and ambient occlusion.
 * <p>
 * Both are drawn at half size into one image (rays in the color, occlusion in the alpha), blurred with
 * regard to depth so the blur does not bleed across edges, then laid over the world image: the occlusion
 * darkens, the rays add light.
 */
public final class PostPass {
    private static final BindGroupLayout INPUT = BindGroupLayout.builder().withSampler("InSampler").build();
    private static final ColorTargetState HALF = new ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL);
    private static final ColorTargetState WORLD = new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR);

    private static final RenderPipeline EFFECTS = pipeline("effects", HALF, null);
    private static final RenderPipeline BLUR = pipeline("effects_blur", HALF, INPUT);
    private static final RenderPipeline COMPOSITE = pipeline("effects_composite", WORLD, INPUT);

    private PostPass() {
    }

    private static RenderPipeline pipeline(final String fragment, final ColorTargetState target, final @Nullable BindGroupLayout input) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(MetallumExtra.MOD_ID, "pipeline/" + fragment))
                .withVertexShader(Identifier.fromNamespaceAndPath(MetallumExtra.MOD_ID, "fullscreen"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(MetallumExtra.MOD_ID, fragment))
                .withColorTargetState(target)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
        if (input != null) builder.withBindGroupLayout(input);
        return builder.build();
    }

    public static void render(final RenderTarget main) {
        ShaderTargets targets = Shaders.targets();
        if (targets.effects() == null || main.width != targets.width() || main.height != targets.height()) return;
        draw(EFFECTS, null, targets.effects().getColorTextureView());
        draw(BLUR, targets.effects().getColorTextureView(), targets.effectsBlurred().getColorTextureView());
        draw(COMPOSITE, targets.effectsBlurred().getColorTextureView(), main.getColorTextureView());
    }

    private static void draw(final RenderPipeline pipeline, final @Nullable GpuTextureView input, final GpuTextureView output) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Metallum Extra effects", output, Optional.empty())) {
            pass.setPipeline(pipeline);
            if (input != null) pass.bindTexture("InSampler", input, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }
}
