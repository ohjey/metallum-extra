package com.metallumextra.mixin;

import com.metallumextra.FrameProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time every real pipeline compile (GLSL -> SPIR-V -> MSL -> Metal library -> pipeline states). */
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
}
