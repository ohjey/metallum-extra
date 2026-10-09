package com.metallumextra.mixin.shader;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.metallumextra.shader.Shaders;
import com.metallumextra.shader.pack.PackManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;

/** Shaders: a pipeline that is compiled as a pass first draws with it; see {@link MetalDeviceShaderMixin}. */
@Mixin(targets = "com.metallum.render.MetalRenderPass", remap = false)
public abstract class MetalRenderPassShaderMixin {
    @WrapMethod(method = "setPipeline")
    private void metallumExtra$fallBackFromBrokenPack(final RenderPipeline pipeline, final Operation<Void> original) {
        try {
            original.call(pipeline);
        } catch (RuntimeException e) {
            if (!Shaders.active() || !PackManager.fail(e)) throw e;
            original.call(pipeline);
        }
    }
}
