package com.metallumextra.mixin;

import com.metallum.render.MetallumExtraBridge;
import com.metallumextra.FrameProfiler;
import com.metallumextra.SamplerSlots;
import com.mojang.blaze3d.GpuFormat;
import org.lwjgl.PointerBuffer;
import org.lwjgl.util.spvc.Spvc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Time every real pipeline compile (GLSL -> SPIR-V -> MSL -> Metal library -> pipeline states), and renumber
 * the samplers of shaders with more textures than Metal has sampler slots for (see {@link SamplerSlots}).
 * <p>
 * Also: the SPIR-V to MSL step is skipped when its result is already on disk (see
 * {@link com.metallumextra.shader.pack.TranslationCache}).
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

    /**
     * Metallum's types are not reachable from here (they are package-private), so the result is passed around as an
     * Object and {@link MetallumExtraBridge} does the work. Looked up here, stored below: if a conversion was needed,
     * its key waits in this thread-local until the result is known.
     */
    @Inject(method = "spirvToMsl", at = @At("HEAD"), cancellable = true)
    private static void metallumExtra$cachedTranslation(final ByteBuffer spirv, final int pushConstantBinding, final Map<String, GpuFormat> attributeFormats,
                                                        final boolean enableFragDepth, final CallbackInfoReturnable<?> cir) {
        Object cached = MetallumExtraBridge.cachedTranslation(spirv, pushConstantBinding, attributeFormats, enableFragDepth);
        if (cached != null) {
            @SuppressWarnings("unchecked") CallbackInfoReturnable<Object> result = (CallbackInfoReturnable<Object>) cir;
            result.setReturnValue(cached);
        }
    }

    @Inject(method = "spirvToMsl", at = @At("RETURN"))
    private static void metallumExtra$storeTranslation(final ByteBuffer spirv, final int pushConstantBinding, final Map<String, GpuFormat> attributeFormats,
                                                       final boolean enableFragDepth, final CallbackInfoReturnable<?> cir) {
        MetallumExtraBridge.storeTranslation(cir.getReturnValue());
    }
}
