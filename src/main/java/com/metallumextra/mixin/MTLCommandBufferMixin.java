package com.metallumextra.mixin;

import com.metallum.mtl.MTLCommandBuffer;
import com.metallumextra.FrameProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Count render/copy passes per frame. On Apple GPUs every pass switch costs a tile store + reload. */
@Mixin(value = MTLCommandBuffer.class, remap = false)
public abstract class MTLCommandBufferMixin {
    @Inject(method = "makeBlitCommandEncoder", at = @At("HEAD"))
    private void metallumExtra$blitPass(final CallbackInfoReturnable<?> cir) {
        FrameProfiler.blitPassStarted();
    }

    @Inject(
            method = "makeRenderCommandEncoder(Lcom/metallum/mtl/MTLRenderPassDescriptor;)Lcom/metallum/mtl/MTLRenderCommandEncoder;",
            at = @At("HEAD")
    )
    private void metallumExtra$renderPass(final CallbackInfoReturnable<?> cir) {
        FrameProfiler.renderPassStarted();
    }
}
