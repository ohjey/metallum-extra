package com.metallumextra.mixin.shader;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.metallumextra.shader.ShaderSources;
import com.metallumextra.shader.Shaders;
import com.metallumextra.shader.pack.PackManager;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Shaders: every shader Metallum compiles is first looked for among this mod's own (see {@link ShaderSources}). */
@Mixin(targets = "com.metallum.render.MetalDevice", remap = false)
public abstract class MetalDeviceShaderMixin {
    @ModifyVariable(method = "getOrCompileShader", at = @At("HEAD"), argsOnly = true)
    private ShaderSource metallumExtra$ownShadersFirst(final ShaderSource shaderSource) {
        return ShaderSources.wrap(shaderSource);
    }

    /**
     * A pipeline is compiled the first time it is used, so the mistakes of a shader pack show up here (and in
     * {@link MetalRenderPassShaderMixin}, for pipelines compiled as they are drawn with) and not when the pack is
     * chosen. Without this the game would crash. The pack is put aside (see {@link PackManager#fail}) and the pipeline
     * is built again from the shaders that took over.
     */
    @WrapMethod(method = "precompilePipeline")
    private CompiledRenderPipeline metallumExtra$fallBackFromBrokenPack(final RenderPipeline pipeline, final ShaderSource shaderSource,
                                                                       final Operation<CompiledRenderPipeline> original) {
        try {
            return original.call(pipeline, shaderSource);
        } catch (RuntimeException e) {
            if (!Shaders.active() || !PackManager.fail(e)) throw e;
            return original.call(pipeline, shaderSource);
        }
    }
}
