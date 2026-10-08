package com.metallumextra.mixin.shader;

import com.llamalad7.mixinextras.sugar.Local;
import com.metallumextra.shader.Shaders;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shaders: the shadow map is drawn after the game has prepared the level's chunks and before it runs any of the
 * level's own passes, which is the one point where the chunk meshes are current and no render pass is open.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererShaderMixin {
    @Inject(method = "render",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;execute(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder$Inspector;)V"))
    private void metallumExtra$beforeWorldPasses(final CallbackInfo ci, @Local final FeatureRenderDispatcher.PreparedFrame featureFrame) {
        Shaders.beforeWorldPasses(featureFrame);
    }
}
