package com.metallumextra.mixin;

import com.metallum.mtl.MTLCommandBuffer;
import com.metallum.mtl.MTLCommandEncoder;
import com.metallum.mtl.MTLFence;
import com.metallum.mtl.MTLRenderCommandEncoder;
import com.metallum.mtl.MTLRenderStages;
import com.metallum.mtl.MetallumExtraTargets;
import com.metallum.render.MetallumExtraBridge;
import com.metallumextra.FrameProfiler;
import com.metallumextra.MetallumExtra;
import com.metallumextra.MultiTarget;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Time the CPU spends blocked waiting for the GPU to finish older frames (or fences), plus multiple render
 * targets: Metallum 0.0.23 starts every render pass with color target 0 only.
 */
@Mixin(targets = "com.metallum.render.MetalCommandEncoder", remap = false)
public abstract class MetalCommandEncoderMixin implements MultiTarget.Encoder {
    @Shadow private MTLCommandEncoder currentEncoder;
    @Shadow private MemorySegment renderColorAttachment;
    @Shadow private MemorySegment renderDepthAttachment;
    @Shadow @Final private MTLFence fence;
    @Shadow @Final private Map<GpuTexture, Vector4fc> pendingColorClears;

    /** The extra color targets of the Metal encoder that is open now; null while it has only target 0. */
    @Unique
    private MemorySegment @Nullable [] metallumExtra$encoderExtraTargets;
    @Unique
    private boolean metallumExtra$loggedExtraTargets;

    @Shadow
    abstract void endEncoder();

    @Shadow
    abstract MTLCommandBuffer commandBuffer();

    @Shadow
    private static boolean isFullTextureView(final GpuTextureView view) {
        throw new AssertionError();
    }

    /** Every buffer/texture upload or copy goes through here (Metallum gives each one its own copy pass). */
    @Inject(method = "blitCommandEncoder", at = @At("HEAD"))
    private void metallumExtra$uploadStarted(final CallbackInfoReturnable<?> cir) {
        FrameProfiler.uploadStarted();
    }

    @Inject(method = "awaitSubmitCompletion", at = @At("HEAD"))
    private void metallumExtra$gpuWaitBegin(final long submitIndex, final long timeoutMs, final CallbackInfoReturnable<Boolean> cir) {
        FrameProfiler.gpuWaitBegin();
    }

    @Inject(method = "awaitSubmitCompletion", at = @At("RETURN"))
    private void metallumExtra$gpuWaitEnd(final long submitIndex, final long timeoutMs, final CallbackInfoReturnable<Boolean> cir) {
        FrameProfiler.gpuWaitEnd();
    }

    /**
     * Metallum has built the pass from color target 0 and the depth target. Hand it the rest, treating each one's
     * deferred clear the same way Metallum treats target 0's.
     */
    @Inject(method = "createRenderPass", at = @At("RETURN"))
    private void metallumExtra$attachExtraTargets(final RenderPassDescriptor descriptor, final CallbackInfoReturnable<RenderPassBackend> cir) {
        List<RenderPassDescriptor.Attachment<Optional<Vector4fc>>> attachments = descriptor.colorAttachments();
        int count = Math.min(attachments.size(), MetallumExtraTargets.MAX_COLOR_TARGETS);
        if (count < 2) return;

        GpuTextureView[] targets = new GpuTextureView[count - 1];
        Vector4fc[] clears = new Vector4fc[count - 1];
        boolean any = false;
        for (int i = 1; i < count; i++) {
            RenderPassDescriptor.Attachment<Optional<Vector4fc>> attachment = attachments.get(i);
            if (attachment == null) continue;
            GpuTextureView view = attachment.textureView();
            GpuTexture texture = view.texture();
            Vector4fc clear = attachment.clearValue().orElse(null);
            Vector4fc pending = this.pendingColorClears.get(texture);
            if (pending != null && clear == null) {
                if (isFullTextureView(view)) {
                    this.pendingColorClears.remove(texture);
                    clear = pending;
                } else {
                    MetallumExtraBridge.flushPendingClear(this, texture);
                }
            } else {
                this.pendingColorClears.remove(texture);
            }
            MetallumExtraBridge.markContentsDirty(texture);
            targets[i - 1] = view;
            clears[i - 1] = clear;
            any = true;
        }
        if (any) {
            ((MultiTarget.Pass) cir.getReturnValue()).metallumExtra$setExtraTargets(this, targets, clears);
            if (!this.metallumExtra$loggedExtraTargets) {
                this.metallumExtra$loggedExtraTargets = true;
                MetallumExtra.LOGGER.info("[Metallum Extra] Multiple render targets in use: first pass with {} color targets is '{}'", count, descriptor.label().get());
            }
        }
    }

    /**
     * Metallum keeps one Metal encoder open across passes that draw into the same color and depth targets. It
     * does not know about the extra targets, so a pass without them must not carry on in an encoder that has them.
     */
    @Inject(method = "renderCommandEncoder", at = @At("HEAD"))
    private void metallumExtra$leaveMultiTargetEncoder(final CallbackInfoReturnable<MTLRenderCommandEncoder> cir) {
        if (this.metallumExtra$encoderExtraTargets != null) this.endEncoder();
    }

    @Inject(method = "endEncoder", at = @At("TAIL"))
    private void metallumExtra$encoderEnded(final CallbackInfo ci) {
        this.metallumExtra$encoderExtraTargets = null;
    }

    @Override
    public MTLRenderCommandEncoder metallumExtra$multiTargetEncoder(final GpuTextureView color, final @Nullable GpuTextureView depth, final @Nullable GpuTextureView[] extraTargets,
                                                                    final @Nullable Vector4fc clearColor, final @Nullable Double clearDepth, final @Nullable Vector4fc @Nullable [] extraClears) {
        MemorySegment colorHandle = MetallumExtraBridge.nativeHandle(color);
        MemorySegment depthHandle = MetallumExtraBridge.nativeHandle(depth);
        MemorySegment[] open = this.metallumExtra$encoderExtraTargets;
        // Metallum clears inside an open encoder by drawing a quad; here a clear simply starts a new encoder.
        if (open != null && clearColor == null && clearDepth == null && extraClears == null
                && this.currentEncoder instanceof MTLRenderCommandEncoder encoder
                && metallumExtra$same(this.renderColorAttachment, colorHandle)
                && metallumExtra$same(this.renderDepthAttachment, depthHandle)
                && metallumExtra$sameTargets(open, extraTargets)) {
            return encoder;
        }

        this.endEncoder();
        MemorySegment[] handles = new MemorySegment[extraTargets.length];
        for (int i = 0; i < handles.length; i++) {
            handles[i] = MetallumExtraBridge.nativeHandle(extraTargets[i]);
        }
        MTLRenderCommandEncoder encoder = MetallumExtraTargets.makeRenderCommandEncoder(this.commandBuffer(), colorHandle, clearColor, handles, extraClears,
                depthHandle, clearDepth, color.getWidth(0), color.getHeight(0));
        encoder.waitForFence(this.fence, MTLRenderStages.VertexAndFragment);
        this.currentEncoder = encoder;
        this.renderColorAttachment = colorHandle;
        this.renderDepthAttachment = depthHandle;
        this.metallumExtra$encoderExtraTargets = handles;
        return encoder;
    }

    @Unique
    private static boolean metallumExtra$sameTargets(final MemorySegment[] open, final @Nullable GpuTextureView[] wanted) {
        if (open.length != wanted.length) return false;
        for (int i = 0; i < open.length; i++) {
            if (!metallumExtra$same(open[i], MetallumExtraBridge.nativeHandle(wanted[i]))) return false;
        }
        return true;
    }

    @Unique
    private static boolean metallumExtra$same(final MemorySegment a, final MemorySegment b) {
        return a.address() == b.address();
    }
}
