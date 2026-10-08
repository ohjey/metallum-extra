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
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Edge smoothing: the last thing done to the world image, before the held item is drawn onto it. The image and
 * its depth are copied, and the image is drawn back with the stair steps blended away along the edges its depth
 * shows (the shader reads the depth copy by name; see {@link ShaderBindings}).
 */
public final class EdgePass {
    private static final RenderPipeline EDGES = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(MetallumExtra.MOD_ID, "pipeline/edges"))
            .withVertexShader(Identifier.fromNamespaceAndPath(MetallumExtra.MOD_ID, "fullscreen"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MetallumExtra.MOD_ID, "edges"))
            .withBindGroupLayout(BindGroupLayout.builder().withSampler("InSampler").build())
            .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    private EdgePass() {
    }

    public static void render(final RenderTarget main) {
        ShaderTargets targets = Shaders.targets();
        if (targets.scene() == null || main.width != targets.width() || main.height != targets.height()) return;
        targets.copyScene(main);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Metallum Extra edges", main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(EDGES);
            pass.bindTexture("InSampler", targets.scene().getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }
}
