package com.metallumextra.mixin;

import com.metallum.mtl.CAMetalLayer;
import com.metallumextra.FrameProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time spent blocked in nextDrawable (waiting for the display/compositor to hand back a swapchain image). */
@Mixin(value = CAMetalLayer.class, remap = false)
public abstract class CAMetalLayerMixin {
    @Inject(method = "nextDrawable", at = @At("HEAD"))
    private void metallumExtra$drawableBegin(final CallbackInfoReturnable<?> cir) {
        FrameProfiler.drawableBegin();
    }

    @Inject(method = "nextDrawable", at = @At("RETURN"))
    private void metallumExtra$drawableEnd(final CallbackInfoReturnable<?> cir) {
        FrameProfiler.drawableEnd();
    }
}
