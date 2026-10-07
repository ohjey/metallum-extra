package com.metallumextra.mixin;

import com.metallum.mtl.MTLRenderCommandEncoder;
import com.metallumextra.FrameProfiler;
import com.metallumextra.MultiTarget;
import com.metallumextra.SamplerSlots;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.foreign.MemorySegment;

/**
 * Profiler: how many terrain draws Metallum issues and how long that takes. Metal has no multi-draw, so
 * Metallum sends each draw in a batch as its own call; these numbers size what a stored draw list could save.
 * <p>
 * Multiple render targets: a Metallum 0.0.23 pass knows one color target. The extra ones are kept here, and a
 * pass that has any gets its Metal encoder from {@link MultiTarget.Encoder} instead of from Metallum.
 */
@Mixin(targets = "com.metallum.render.MetalRenderPass", remap = false)
public abstract class MetalRenderPassMixin implements MultiTarget.Pass {
    @Shadow @Final private GpuTextureView colorTexture;
    @Shadow @Final private GpuTextureView depthTexture;
    @Shadow private Vector4fc clearColor;
    @Shadow private Double clearDepth;

    @Unique
    private long metallumExtra$batchStart;
    @Unique
    private MultiTarget.@Nullable Encoder metallumExtra$encoder;
    @Unique
    private @Nullable GpuTextureView @Nullable [] metallumExtra$extraTargets;
    /** Clears still to be done for the extra targets; null once the encoder has been started with them. */
    @Unique
    private @Nullable Vector4fc @Nullable [] metallumExtra$extraClears;
    /** Bit {@code i - 1} is set when color target {@code i} is attached. */
    @Unique
    private int metallumExtra$attachedTargets;

    /** The sampler renumbering of the pipeline that is bound now; null for nearly every pipeline. */
    @Unique
    private byte @Nullable [] metallumExtra$samplerSlots;

    @Shadow
    protected abstract MTLRenderCommandEncoder renderEncoder();

    @Shadow
    private static void bindTextureAndSampler(final MTLRenderCommandEncoder encoder, final MemorySegment texture, final MemorySegment sampler, final long index, final int stageMask) {
        throw new AssertionError();
    }

    @Inject(method = "drawIndexedIndirect", at = @At("HEAD"))
    private void metallumExtra$batchBegin(final GpuBufferSlice commands, final int drawCount, final CallbackInfo ci) {
        if (FrameProfiler.enabled()) this.metallumExtra$batchStart = System.nanoTime();
    }

    @Inject(method = "drawIndexedIndirect", at = @At("RETURN"))
    private void metallumExtra$batchEnd(final GpuBufferSlice commands, final int drawCount, final CallbackInfo ci) {
        if (FrameProfiler.enabled()) FrameProfiler.drawBatchSubmitted(drawCount, System.nanoTime() - this.metallumExtra$batchStart);
    }

    @Override
    public void metallumExtra$setExtraTargets(final MultiTarget.Encoder encoder, final @Nullable GpuTextureView[] targets, final @Nullable Vector4fc[] clears) {
        int attached = 0;
        boolean anyClear = false;
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] != null) attached |= 1 << i;
            if (clears[i] != null) anyClear = true;
        }
        this.metallumExtra$encoder = encoder;
        this.metallumExtra$extraTargets = targets;
        this.metallumExtra$extraClears = anyClear ? clears : null;
        this.metallumExtra$attachedTargets = attached;
    }

    @Inject(method = "renderEncoder", at = @At("HEAD"), cancellable = true)
    private void metallumExtra$multiTargetEncoder(final CallbackInfoReturnable<MTLRenderCommandEncoder> cir) {
        GpuTextureView[] extraTargets = this.metallumExtra$extraTargets;
        if (extraTargets == null) return;
        MTLRenderCommandEncoder encoder = this.metallumExtra$encoder.metallumExtra$multiTargetEncoder(this.colorTexture, this.depthTexture, extraTargets,
                this.clearColor, this.clearDepth, this.metallumExtra$extraClears);
        this.clearColor = null;
        this.clearDepth = null;
        this.metallumExtra$extraClears = null;
        cir.setReturnValue(encoder);
    }

    /** Metallum makes sure a pass's clears happen even if it draws nothing; do the same for the extra targets. */
    @Inject(method = "materializePendingClear", at = @At("HEAD"))
    private void metallumExtra$materializeExtraClears(final CallbackInfo ci) {
        if (this.metallumExtra$extraClears != null) this.renderEncoder();
    }

    /**
     * A pipeline that writes several color targets has one native pipeline per combination of attached targets.
     * This is also where the pass learns which pipeline it is about to bind resources for.
     */
    @Redirect(method = "bindDrawState",
            at = @At(value = "INVOKE", target = "Lcom/metallum/render/MetalCompiledRenderPipeline;getNativePipeline(Z)Ljava/lang/foreign/MemorySegment;"))
    private MemorySegment metallumExtra$pipelineForAttachedTargets(final @Coerce Object pipeline, final boolean depth) {
        this.metallumExtra$samplerSlots = ((SamplerSlots.Pipeline) pipeline).metallumExtra$samplerSlots();
        return ((MultiTarget.Pipeline) pipeline).metallumExtra$nativePipeline(depth, this.metallumExtra$attachedTargets);
    }

    /** Metallum binds a texture and its sampler under the same number; a renumbered shader expects the sampler elsewhere. */
    @Redirect(method = "pushDescriptor",
            at = @At(value = "INVOKE", target = "Lcom/metallum/render/MetalRenderPass;bindTextureAndSampler(Lcom/metallum/mtl/MTLRenderCommandEncoder;Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;JI)V"))
    private void metallumExtra$bindWithSamplerSlot(final MTLRenderCommandEncoder encoder, final MemorySegment texture, final MemorySegment sampler, final long index, final int stageMask) {
        byte[] slots = this.metallumExtra$samplerSlots;
        if (slots == null) {
            bindTextureAndSampler(encoder, texture, sampler, index, stageMask);
            return;
        }
        if ((stageMask & SamplerSlots.STAGE_VERTEX) != 0) {
            encoder.setVertexTexture(texture, index);
            encoder.setVertexSamplerState(sampler, slots[(int) index * 2]);
        }
        if ((stageMask & SamplerSlots.STAGE_FRAGMENT) != 0) {
            encoder.setFragmentTexture(texture, index);
            encoder.setFragmentSamplerState(sampler, slots[(int) index * 2 + 1]);
        }
    }
}
