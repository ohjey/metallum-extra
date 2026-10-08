package com.metallumextra.shader;

import com.metallumextra.MetallumExtra;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * The sky. The game clears the image to its fog color and draws a flat, sky-colored disc overhead; this draws
 * every pixel of the sky from one function instead, the same function terrain fog fades to. The game's sun,
 * moon and stars are drawn on top as usual.
 */
public final class SkyPass {
    private static final RenderPipeline SKY = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(MetallumExtra.MOD_ID, "pipeline/sky"))
            .withVertexShader(Identifier.fromNamespaceAndPath(MetallumExtra.MOD_ID, "fullscreen"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(MetallumExtra.MOD_ID, "sky"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    private SkyPass() {
    }

    public static void render(final RenderTarget target) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Metallum Extra sky", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(SKY);
            pass.draw(3, 1, 0, 0);
        }
    }
}
