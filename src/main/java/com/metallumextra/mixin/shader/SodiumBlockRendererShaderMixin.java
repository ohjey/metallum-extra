package com.metallumextra.mixin.shader;

import com.metallumextra.shader.BlockTypes;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shaders: notes which block Sodium is meshing, so its quads can be tagged with the block's kind (see {@link BlockTypes}). */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumBlockRendererShaderMixin {
    @Inject(method = "renderModel", at = @At("HEAD"))
    private void metallumExtra$beginBlock(final BlockStateModel model, final BlockState state, final BlockPos pos, final BlockPos origin, final CallbackInfo ci) {
        BlockTypes.begin(state);
    }

    @Inject(method = "renderModel", at = @At("RETURN"))
    private void metallumExtra$endBlock(final CallbackInfo ci) {
        BlockTypes.end();
    }
}
