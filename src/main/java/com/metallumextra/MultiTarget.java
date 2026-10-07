package com.metallumextra;

import com.metallum.mtl.MTLRenderCommandEncoder;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import java.lang.foreign.MemorySegment;

/**
 * Multiple render targets: what the mixins add to Metallum's classes, so they can call each other.
 * Metallum's own classes are package-private, hence the interfaces.
 */
public final class MultiTarget {
    private MultiTarget() {
    }

    /** Added to MetalRenderPass. */
    public interface Pass {
        /** {@code targets[i]} is color target {@code i + 1}; null entries are unused. {@code clears} lines up with it. */
        void metallumExtra$setExtraTargets(Encoder encoder, @Nullable GpuTextureView[] targets, @Nullable Vector4fc[] clears);
    }

    /** Added to MetalCommandEncoder. */
    public interface Encoder {
        /** The Metal encoder drawing into all of these targets, started now if the current one does not match. */
        MTLRenderCommandEncoder metallumExtra$multiTargetEncoder(GpuTextureView color, @Nullable GpuTextureView depth, @Nullable GpuTextureView[] extraTargets,
                                                                 @Nullable Vector4fc clearColor, @Nullable Double clearDepth, @Nullable Vector4fc @Nullable [] extraClears);
    }

    /** Added to MetalCompiledRenderPipeline. */
    public interface Pipeline {
        /** The native pipeline for a pass with these extra targets attached (bit {@code i - 1} = color target {@code i}). */
        MemorySegment metallumExtra$nativePipeline(boolean depth, int attachedTargets);
    }
}
