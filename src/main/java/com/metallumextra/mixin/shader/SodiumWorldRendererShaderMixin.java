package com.metallumextra.mixin.shader;

import com.metallumextra.shader.Shaders;
import com.metallumextra.shader.sodium.SodiumHooks;
import com.mojang.blaze3d.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shaders: the moment just before Sodium draws translucent terrain, when the world behind the water is complete;
 * and a way for the shadow pass to reach Sodium's chunk meshes.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class SodiumWorldRendererShaderMixin implements SodiumHooks.WorldRenderer {
    @Shadow private RenderSectionManager renderSectionManager;

    @Override
    public RenderSectionManager metallumExtra$sections() {
        return this.renderSectionManager;
    }

    @Inject(method = "drawChunkLayer", at = @At("HEAD"))
    private void metallumExtra$beforeTranslucentTerrain(final ChunkSectionLayerGroup group, final ChunkRenderMatrices matrices, final double x, final double y, final double z,
                                                        final GpuSampler terrainSampler, final CallbackInfo ci) {
        if (group == ChunkSectionLayerGroup.TRANSLUCENT) Shaders.beforeTranslucentTerrain();
    }
}
