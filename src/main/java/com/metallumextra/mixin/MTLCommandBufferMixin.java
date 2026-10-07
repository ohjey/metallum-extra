package com.metallumextra.mixin;

import com.metallum.mtl.MTLCommandBuffer;
import com.metallum.objc.Msg;
import com.metallum.objc.ObjC;
import com.metallumextra.FrameProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Count render/copy passes per frame (on Apple GPUs every pass switch costs a tile store + reload), and read back
 * Metal's own timestamps for each frame's command buffer: how long it waited to be scheduled and how long the GPU
 * actually spent executing it.
 */
@Mixin(value = MTLCommandBuffer.class, remap = false)
public abstract class MTLCommandBufferMixin {
    @Unique private static final Msg metallumExtra$KERNEL_START = Msg.of("kernelStartTime", ValueLayout.JAVA_DOUBLE);
    @Unique private static final Msg metallumExtra$KERNEL_END = Msg.of("kernelEndTime", ValueLayout.JAVA_DOUBLE);
    @Unique private static final Msg metallumExtra$GPU_START = Msg.of("GPUStartTime", ValueLayout.JAVA_DOUBLE);
    @Unique private static final Msg metallumExtra$GPU_END = Msg.of("GPUEndTime", ValueLayout.JAVA_DOUBLE);

    @Shadow private MemorySegment handle;

    @Unique private long metallumExtra$commitNs;
    @Unique private long metallumExtra$frameStats;

    @Inject(method = "commitWithCompletionBlock", at = @At("HEAD"))
    private void metallumExtra$committed(final MemorySegment block, final CallbackInfo ci) {
        this.metallumExtra$commitNs = System.nanoTime();
        this.metallumExtra$frameStats = FrameProfiler.frameStatsForGpu();
    }

    /** Metallum closes a frame's command buffer only after it has completed, so the timestamps are final here. */
    @Inject(method = "close", at = @At("HEAD"))
    private void metallumExtra$readGpuTimes(final CallbackInfo ci) {
        if (this.metallumExtra$commitNs == 0L || ObjC.isNil(this.handle)) return;
        FrameProfiler.gpuFrameCompleted(this.metallumExtra$commitNs, this.metallumExtra$frameStats,
                metallumExtra$KERNEL_START.sendDouble(this.handle), metallumExtra$KERNEL_END.sendDouble(this.handle),
                metallumExtra$GPU_START.sendDouble(this.handle), metallumExtra$GPU_END.sendDouble(this.handle));
        this.metallumExtra$commitNs = 0L;
    }

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
