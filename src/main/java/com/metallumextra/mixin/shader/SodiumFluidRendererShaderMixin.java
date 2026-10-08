package com.metallumextra.mixin.shader;

import com.metallumextra.shader.BlockTypes;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shaders: notes which fluid Sodium is meshing, so its quads can be tagged as water or lava (see {@link BlockTypes}). */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer", remap = false)
public abstract class SodiumFluidRendererShaderMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void metallumExtra$beginFluid(final LevelSlice level, final BlockState blockState, final FluidState fluidState, final BlockPos blockPos, final BlockPos offset,
                                          final TranslucentGeometryCollector collector, final ChunkModelBuilder meshBuilder, final Material material,
                                          final ColorProvider<FluidState> colorProvider, final FluidModel sprites, final CallbackInfo ci) {
        BlockTypes.begin(fluidState);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void metallumExtra$endFluid(final CallbackInfo ci) {
        BlockTypes.end();
    }
}
