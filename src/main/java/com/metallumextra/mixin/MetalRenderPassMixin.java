package com.metallumextra.mixin;

import com.metallumextra.FrameProfiler;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Profiler only: how many terrain draws Metallum issues and how long that takes. Metal has no multi-draw, so
 * Metallum sends each draw in a batch as its own call; these numbers size what a stored draw list could save.
 */
@Mixin(targets = "com.metallum.render.MetalRenderPass", remap = false)
public abstract class MetalRenderPassMixin {
    @Unique
    private long metallumExtra$batchStart;

    @Inject(method = "drawIndexedIndirect", at = @At("HEAD"))
    private void metallumExtra$batchBegin(final GpuBufferSlice commands, final int drawCount, final CallbackInfo ci) {
        this.metallumExtra$batchStart = System.nanoTime();
    }

    @Inject(method = "drawIndexedIndirect", at = @At("RETURN"))
    private void metallumExtra$batchEnd(final GpuBufferSlice commands, final int drawCount, final CallbackInfo ci) {
        FrameProfiler.drawBatchSubmitted(drawCount, System.nanoTime() - this.metallumExtra$batchStart);
    }
}
