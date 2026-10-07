package com.metallum.mtl;

import com.metallum.objc.AutoreleasePool;
import com.metallum.objc.ObjC;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.util.Optional;

/**
 * Multiple render targets. Lives in Metallum's package to reach MTLCommandBuffer.makeRenderCommandEncoder(descriptor).
 * <p>
 * Metallum 0.0.23 only ever attaches color target 0, both when it starts a render pass and when it builds a
 * pipeline. Its Metal wrappers already take a target index, so everything here is the same calls Metallum makes
 * for target 0, repeated for targets 1 and up.
 */
public final class MetallumExtraTargets {
    /** Metal's limit on color attachments in one render pass. */
    public static final int MAX_COLOR_TARGETS = 8;

    private static final long LOAD_ACTION_LOAD = 1L;
    private static final long LOAD_ACTION_CLEAR = 2L;
    private static final long STORE_ACTION_STORE = 1L;

    /** The pipeline being built on this thread, if it declares more than one color target. */
    private static final ThreadLocal<Templates> BUILDING = new ThreadLocal<>();

    private MetallumExtraTargets() {
    }

    /**
     * Same as MTLCommandBuffer.makeRenderCommandEncoder(color, clearColor, depth, clearDepth, width, height) in
     * Metallum 0.0.23, plus the extra color targets. {@code extraTargets[i]} is color target {@code i + 1}; NULL
     * entries are left unattached.
     */
    public static MTLRenderCommandEncoder makeRenderCommandEncoder(final MTLCommandBuffer commandBuffer, final MemorySegment color, final @Nullable Vector4fc clearColor,
                                                                   final MemorySegment[] extraTargets, final @Nullable Vector4fc @Nullable [] extraClears,
                                                                   final MemorySegment depth, final @Nullable Double clearDepth, final double width, final double height) {
        try (AutoreleasePool pool = AutoreleasePool.push(); MTLRenderPassDescriptor descriptor = new MTLRenderPassDescriptor()) {
            descriptor.colorAttachment(0L, color, clearColor != null ? LOAD_ACTION_CLEAR : LOAD_ACTION_LOAD, STORE_ACTION_STORE, clearColor);
            for (int i = 0; i < extraTargets.length; i++) {
                if (ObjC.isNil(extraTargets[i])) continue;
                Vector4fc clear = extraClears == null ? null : extraClears[i];
                descriptor.colorAttachment(i + 1, extraTargets[i], clear != null ? LOAD_ACTION_CLEAR : LOAD_ACTION_LOAD, STORE_ACTION_STORE, clear);
            }
            if (!ObjC.isNil(depth)) {
                descriptor.depthAttachment(depth, clearDepth != null ? LOAD_ACTION_CLEAR : LOAD_ACTION_LOAD, STORE_ACTION_STORE, clearDepth);
                if (MTLPixelFormat.hasStencil(MTLTexture.pixelFormat(depth))) {
                    descriptor.stencilAttachment(depth, 0L, 0L);
                }
            }
            MTLRenderCommandEncoder encoder = commandBuffer.makeRenderCommandEncoder(descriptor);
            encoder.setViewport(0.0, 0.0, width, height, 0.0, 1.0);
            return encoder;
        }
    }

    /**
     * Called just before Metallum builds a native pipeline. Adds the pipeline's color targets 1 and up to the
     * descriptor, and keeps the descriptor so other combinations of attached targets can be built later.
     */
    public static void beforePipelineBuild(final RenderPipeline pipeline, final MTLDevice device, final MTLRenderPipelineDescriptor descriptor, final boolean withDepth) {
        ColorTargetState[] targets = pipeline.getColorTargetStates();
        int declared = declaredMask(targets);
        if (declared == 0) return;
        configure(descriptor, targets, declared);

        Templates templates = BUILDING.get();
        if (templates == null || templates.pipeline != pipeline) {
            if (templates != null) templates.close();
            templates = new Templates(pipeline, device, targets, declared);
            BUILDING.set(templates);
        }
        if (withDepth) {
            templates.withDepth = descriptor;
        } else {
            templates.withoutDepth = descriptor;
        }
    }

    /** Whether this descriptor was kept by {@link #beforePipelineBuild} and so must not be closed yet. */
    public static boolean isKept(final MTLRenderPipelineDescriptor descriptor) {
        Templates templates = BUILDING.get();
        return templates != null && (templates.withDepth == descriptor || templates.withoutDepth == descriptor);
    }

    /** The descriptors kept while {@code pipeline} was being built, or null if it has a single color target. */
    public static @Nullable Templates take(final RenderPipeline pipeline) {
        Templates templates = BUILDING.get();
        if (templates == null) return null;
        BUILDING.remove();
        if (templates.pipeline != pipeline) {
            templates.close();
            return null;
        }
        return templates;
    }

    /** Bit {@code i - 1} is set when the pipeline writes color target {@code i}. Target 0 is Metallum's business. */
    private static int declaredMask(final ColorTargetState[] targets) {
        int mask = 0;
        for (int i = 1; i < targets.length && i < MAX_COLOR_TARGETS; i++) {
            if (targets[i] != null) mask |= 1 << (i - 1);
        }
        return mask;
    }

    /** Mirrors what MetalCompiledRenderPipeline.createPipeline does for target 0. Targets not in {@code attached} are switched off. */
    private static void configure(final MTLRenderPipelineDescriptor descriptor, final ColorTargetState[] targets, final int attached) {
        for (int i = 1; i < targets.length && i < MAX_COLOR_TARGETS; i++) {
            ColorTargetState target = targets[i];
            if (target == null) continue;
            if ((attached & (1 << (i - 1))) == 0) {
                descriptor.setColorAttachmentFormat(i, MTLPixelFormat.Invalid);
                descriptor.disableBlending(i, MTLColorWriteMask.All.value);
                continue;
            }
            long writeMask = MTLColorWriteMask.from(target.writeMask());
            descriptor.setColorAttachmentFormat(i, MTLPixelFormat.from(target.format()));
            Optional<BlendFunction> blend = target.blendFunction();
            if (blend.isPresent()) {
                BlendFunction function = blend.get();
                descriptor.setBlendState(i,
                        MTLBlendFactor.from(function.color().sourceFactor()), MTLBlendFactor.from(function.color().destFactor()), MTLBlendOperation.from(function.color().op()),
                        MTLBlendFactor.from(function.alpha().sourceFactor()), MTLBlendFactor.from(function.alpha().destFactor()), MTLBlendOperation.from(function.alpha().op()),
                        writeMask);
            } else {
                descriptor.disableBlending(i, writeMask);
            }
        }
    }

    /**
     * What is needed to build one pipeline for a different set of attached color targets. Metal wants a pipeline's
     * target formats to match the render pass exactly, and a mod may leave some of the targets it declared
     * unattached (Shine does when bloom or rim light is off), so each combination in use gets its own native pipeline.
     */
    public static final class Templates implements AutoCloseable {
        private final RenderPipeline pipeline;
        private final MTLDevice device;
        private final ColorTargetState[] targets;
        private final int declared;
        private @Nullable MTLRenderPipelineDescriptor withDepth;
        private @Nullable MTLRenderPipelineDescriptor withoutDepth;

        private Templates(final RenderPipeline pipeline, final MTLDevice device, final ColorTargetState[] targets, final int declared) {
            this.pipeline = pipeline;
            this.device = device;
            this.targets = targets;
            this.declared = declared;
        }

        /** The extra targets this pipeline writes, in the same bit layout as a render pass's attached mask. */
        public int declared() {
            return declared;
        }

        /** Builds the native pipeline for the given attached targets. Returns NULL if Metal rejects it. */
        public MemorySegment build(final boolean depth, final int attached) {
            MTLRenderPipelineDescriptor descriptor = depth ? withDepth : withoutDepth;
            if (descriptor == null) return MemorySegment.NULL;
            configure(descriptor, targets, attached);
            return device.newRenderPipelineState(descriptor);
        }

        @Override
        public void close() {
            if (withDepth != null) withDepth.close();
            if (withoutDepth != null) withoutDepth.close();
            withDepth = null;
            withoutDepth = null;
        }
    }
}
