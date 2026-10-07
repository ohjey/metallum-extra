package com.metallumextra.mixin;

import com.metallum.mtl.MetallumExtraTargets;
import com.metallum.render.MetallumExtraBridge;
import com.metallumextra.ExtraConfig;
import com.metallumextra.FrameProfiler;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * Pipeline naming for the profiler, plus a fix: in Metallum 0.0.23, creating a buffer with initial data always records a GPU copy,
 * which ends the current render pass. For CPU-visible buffers we can just write the memory.
 * (Metallum's newer source does the same.)
 * <p>
 * Also the device limit for multiple render targets: Metallum 0.0.23 tells the game it supports one color target
 * per render pass, and the game refuses any pass that asks for more.
 */
@Mixin(targets = "com.metallum.render.MetalDevice", remap = false)
public abstract class MetalDeviceMixin {
    /** Remember which pipeline is being looked up, so a compile that follows can be named in the log. */
    @Inject(method = "getOrCompilePipeline", at = @At("HEAD"))
    private void metallumExtra$pipelineLookup(final RenderPipeline pipeline, final CallbackInfoReturnable<?> cir) {
        FrameProfiler.pipelineRequested(pipeline);
    }

    @Inject(method = "precompilePipeline", at = @At("HEAD"))
    private void metallumExtra$pipelinePrecompile(final RenderPipeline pipeline, final ShaderSource shaderSource, final CallbackInfoReturnable<?> cir) {
        FrameProfiler.pipelineRequested(pipeline);
    }

    @Inject(
            method = "createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void metallumExtra$createBufferDirect(final Supplier<String> label, final int usage, final ByteBuffer data, final CallbackInfoReturnable<GpuBuffer> cir) {
        if (ExtraConfig.get().directBufferUpload) {
            GpuBuffer buffer = MetallumExtraBridge.tryCreateBufferDirect(this, label, usage, data);
            if (buffer != null) {
                FrameProfiler.bufferCreatedWithData(true);
                cir.setReturnValue(buffer);
                return;
            }
        }
        FrameProfiler.bufferCreatedWithData(false);
    }

    @ModifyArg(method = "buildDeviceInfo",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/DeviceLimits;<init>(IIIJII)V"), index = 5)
    private int metallumExtra$maxColorAttachments(final int maxColorAttachments) {
        return ExtraConfig.get().multipleRenderTargets ? MetallumExtraTargets.MAX_COLOR_TARGETS : maxColorAttachments;
    }
}
