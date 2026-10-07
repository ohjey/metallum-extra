package com.metallumextra.mixin;

import com.metallumextra.FrameProfiler;
import com.metallumextra.SamplerSlots;
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.spvc.Spvc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Time every real pipeline compile (GLSL -> SPIR-V -> MSL -> Metal library -> pipeline states), and renumber
 * the samplers of shaders with more textures than Metal has sampler slots for (see {@link SamplerSlots}).
 */
@Mixin(targets = "com.metallum.render.MetalCrossShaderCompiler", remap = false)
public abstract class MetalCrossShaderCompilerMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private static void metallumExtra$compileBegin(final CallbackInfoReturnable<?> cir) {
        FrameProfiler.compileBegin();
    }

    @Inject(method = "compile", at = @At("RETURN"))
    private static void metallumExtra$compileEnd(final CallbackInfoReturnable<?> cir) {
        FrameProfiler.compileEnd();
    }

    @Redirect(method = "spirvToMsl",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/spvc/Spvc;spvc_compiler_compile(JLorg/lwjgl/PointerBuffer;)I"))
    private static int metallumExtra$compactSamplerSlots(final long compiler, final PointerBuffer source) {
        SamplerSlots.compact(compiler);
        return Spvc.spvc_compiler_compile(compiler, source);
    }
}
