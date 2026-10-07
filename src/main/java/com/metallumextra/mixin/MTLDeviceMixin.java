package com.metallumextra.mixin;

import com.metallum.mtl.MTLDevice;
import com.metallumextra.FrameProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.foreign.MemorySegment;

/** Time and count Metal buffer/texture allocations made mid-frame. */
@Mixin(value = MTLDevice.class, remap = false)
public abstract class MTLDeviceMixin {
    @Shadow
    public abstract MemorySegment handle();

    @Inject(method = "newBuffer", at = @At("HEAD"))
    private void metallumExtra$bufferBegin(final CallbackInfoReturnable<?> cir) {
        FrameProfiler.allocBegin();
    }

    @Inject(method = "newBuffer", at = @At("RETURN"))
    private void metallumExtra$bufferEnd(final CallbackInfoReturnable<?> cir) {
        FrameProfiler.deviceSeen(this.handle());
        FrameProfiler.allocEnd(false);
    }

    @Inject(method = "newTexture", at = @At("HEAD"))
    private void metallumExtra$textureBegin(final CallbackInfoReturnable<?> cir) {
        FrameProfiler.allocBegin();
    }

    @Inject(method = "newTexture", at = @At("RETURN"))
    private void metallumExtra$textureEnd(final CallbackInfoReturnable<?> cir) {
        FrameProfiler.allocEnd(true);
    }
}
