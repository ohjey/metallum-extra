package com.metallumextra.mixin.shader;

import com.metallumextra.shader.ShaderSources;
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
}
