package com.metallumextra.mixin;

import com.metallumextra.FrameProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time the CPU spends blocked waiting for the GPU to finish older frames (or fences). */
@Mixin(targets = "com.metallum.render.MetalCommandEncoder", remap = false)
public abstract class MetalCommandEncoderMixin {
    @Inject(method = "awaitSubmitCompletion", at = @At("HEAD"))
    private void metallumExtra$gpuWaitBegin(final long submitIndex, final long timeoutMs, final CallbackInfoReturnable<Boolean> cir) {
        FrameProfiler.gpuWaitBegin();
    }

    @Inject(method = "awaitSubmitCompletion", at = @At("RETURN"))
    private void metallumExtra$gpuWaitEnd(final long submitIndex, final long timeoutMs, final CallbackInfoReturnable<Boolean> cir) {
        FrameProfiler.gpuWaitEnd();
    }
}
