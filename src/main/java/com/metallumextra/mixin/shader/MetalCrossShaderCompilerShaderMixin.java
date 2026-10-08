package com.metallumextra.mixin.shader;

import com.metallumextra.shader.ShaderBindings;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Shaders: Metallum refuses a shader that reads a uniform block or texture its pipeline did not declare. The
 * shader pipeline's own names (see {@link ShaderBindings}) are added to what every pipeline is taken to declare.
 */
@Mixin(targets = "com.metallum.render.MetalCrossShaderCompiler", remap = false)
public abstract class MetalCrossShaderCompilerShaderMixin {
    @Redirect(method = "addToBindGroup",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/BindGroupLayout;flattenUniforms(Ljava/util/List;)Ljava/util/List;"))
    private static List<BindGroupLayout.UniformDescription> metallumExtra$allowShaderUniforms(final List<BindGroupLayout> layouts) {
        List<BindGroupLayout.UniformDescription> uniforms = BindGroupLayout.flattenUniforms(layouts);
        ShaderBindings.addUniforms(uniforms);
        return uniforms;
    }

    @Redirect(method = "addToBindGroup",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/BindGroupLayout;flattenSamplers(Ljava/util/List;)Ljava/util/List;"))
    private static List<String> metallumExtra$allowShaderSamplers(final List<BindGroupLayout> layouts) {
        List<String> samplers = BindGroupLayout.flattenSamplers(layouts);
        ShaderBindings.addSamplers(samplers);
        return samplers;
    }
}
