package com.metallumextra.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.metallum.mtl.MTLDevice;
import com.metallum.mtl.MTLPixelFormat;
import com.metallum.mtl.MTLRenderPipelineDescriptor;
import com.metallum.mtl.MetallumExtraTargets;
import com.metallum.objc.ObjC;
import com.metallum.render.MetallumExtraBridge;
import com.metallumextra.ExtraConfig;
import com.metallumextra.MetallumExtra;
import com.metallumextra.MultiTarget;
import com.metallumextra.SamplerSlots;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.foreign.MemorySegment;

/**
 * Multiple render targets, pipeline side. Metallum 0.0.23 builds every pipeline with color target 0 only.
 * For a pipeline that declares more, add the rest, and keep what is needed to build it again for a pass that
 * leaves some of those targets unattached.
 * <p>
 * Also holds the pipeline's sampler renumbering, if it needed one (see {@link SamplerSlots}).
 */
@Mixin(targets = "com.metallum.render.MetalCompiledRenderPipeline", remap = false)
public abstract class MetalCompiledRenderPipelineMixin implements MultiTarget.Pipeline, SamplerSlots.Pipeline {
    @Unique
    private MetallumExtraTargets.@Nullable Templates metallumExtra$templates;
    /** Native pipelines for target combinations other than "all attached", keyed by attached mask and depth. */
    @Unique
    private @Nullable Int2ObjectOpenHashMap<MemorySegment> metallumExtra$variants;

    @Unique
    private byte @Nullable [] metallumExtra$samplerSlots;

    @Shadow
    abstract MemorySegment getNativePipeline(boolean depth);

    @Redirect(method = "createPipeline",
            at = @At(value = "INVOKE", target = "Lcom/metallum/mtl/MTLDevice;newRenderPipelineState(Lcom/metallum/mtl/MTLRenderPipelineDescriptor;)Ljava/lang/foreign/MemorySegment;"))
    private static MemorySegment metallumExtra$addExtraTargets(final MTLDevice device, final MTLRenderPipelineDescriptor descriptor,
                                                               @Local(argsOnly = true) final RenderPipeline pipeline,
                                                               @Local(argsOnly = true, ordinal = 1) final MTLPixelFormat depthFormat) {
        if (ExtraConfig.get().multipleRenderTargets) {
            MetallumExtraTargets.beforePipelineBuild(pipeline, device, descriptor, depthFormat != MTLPixelFormat.Invalid);
        }
        return device.newRenderPipelineState(descriptor);
    }

    /** Metallum closes the descriptor as soon as the pipeline is built; a kept one is closed with the pipeline instead. */
    @Redirect(method = "createPipeline",
            at = @At(value = "INVOKE", target = "Lcom/metallum/mtl/MTLRenderPipelineDescriptor;close()V"))
    private static void metallumExtra$keepDescriptor(final MTLRenderPipelineDescriptor descriptor) {
        if (!MetallumExtraTargets.isKept(descriptor)) descriptor.close();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void metallumExtra$adoptTemplates(final CallbackInfo ci, @Local(argsOnly = true) final RenderPipeline pipeline) {
        this.metallumExtra$templates = MetallumExtraTargets.take(pipeline);
        this.metallumExtra$samplerSlots = MetallumExtraBridge.samplerSlots(this);
    }

    @Override
    public byte @Nullable [] metallumExtra$samplerSlots() {
        return this.metallumExtra$samplerSlots;
    }

    @Override
    public MemorySegment metallumExtra$nativePipeline(final boolean depth, final int attachedTargets) {
        MetallumExtraTargets.Templates templates = this.metallumExtra$templates;
        if (templates == null) return this.getNativePipeline(depth);
        int attached = attachedTargets & templates.declared();
        if (attached == templates.declared()) return this.getNativePipeline(depth);

        if (this.metallumExtra$variants == null) this.metallumExtra$variants = new Int2ObjectOpenHashMap<>();
        int key = attached << 1 | (depth ? 1 : 0);
        MemorySegment variant = this.metallumExtra$variants.get(key);
        if (variant == null) {
            variant = templates.build(depth, attached);
            this.metallumExtra$variants.put(key, variant);
            MetallumExtra.LOGGER.info("[Metallum Extra] Built a pipeline variant: {} of its extra color targets attached{}",
                    Integer.bitCount(attached) + "/" + Integer.bitCount(templates.declared()), ObjC.isNil(variant) ? " (FAILED)" : "");
        }
        return variant;
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void metallumExtra$closeVariants(final CallbackInfo ci) {
        if (this.metallumExtra$variants != null) {
            for (MemorySegment variant : this.metallumExtra$variants.values()) {
                if (!ObjC.isNil(variant)) ObjC.release(variant);
            }
            this.metallumExtra$variants = null;
        }
        if (this.metallumExtra$templates != null) {
            this.metallumExtra$templates.close();
            this.metallumExtra$templates = null;
        }
    }
}
