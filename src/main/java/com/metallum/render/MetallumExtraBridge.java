package com.metallum.render;

import com.metallumextra.SamplerSlots;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jspecify.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Lives in Metallum's package (inside the Metallum Extra jar) so it can reach Metallum's
 * package-private classes. Fabric loads all mods with one class loader, so this works at runtime.
 * Keep this class tiny: everything here depends on Metallum internals, and has to be right for every
 * Metallum release the mod accepts.
 */
public final class MetallumExtraBridge {
    private MetallumExtraBridge() {
    }

    /**
     * For Metallum 0.0.23. Mirrors MetalDevice.createBuffer(label, usage, data) from Metallum 0.0.24:
     * if the new buffer lives in CPU-visible (shared) memory, copy the data straight in
     * instead of recording a GPU blit. Returns null when the buffer is GPU-private, so the
     * original code path (staging + blit) runs.
     */
    public static @Nullable GpuBuffer tryCreateBufferDirect(final Object device, final @Nullable Supplier<String> label, final int usage, final ByteBuffer data) {
        int fullUsage = usage | GpuBuffer.USAGE_COPY_DST;
        if (!isSharedMemory(fullUsage)) {
            return null;
        }
        MetalDevice metalDevice = (MetalDevice) device;
        MetalGpuBuffer buffer = (MetalGpuBuffer) metalDevice.createBuffer(label, fullUsage, data.remaining());
        buffer.currentStorage().put(0, data, data.position(), data.remaining());
        return buffer;
    }

    /** Same rule as MetalGpuBuffer.toMtlResourceOptions in Metallum 0.0.23 (Shared storage mode). */
    private static boolean isSharedMemory(final int usage) {
        boolean cpuAccessible = (usage & GpuBuffer.USAGE_MAP_READ) != 0
                || (usage & GpuBuffer.USAGE_MAP_WRITE) != 0
                || (usage & GpuBuffer.USAGE_HINT_CLIENT_STORAGE) != 0;
        boolean dynamic = (usage & GpuBuffer.USAGE_UNIFORM) != 0 && (usage & GpuBuffer.USAGE_COPY_DST) != 0;
        return cpuAccessible || dynamic;
    }

    /**
     * Whether Metallum 0.0.24 itself writes a new buffer of this usage directly. Its rule is the one above, plus
     * index buffers, which it keeps in shared memory. Only for the profiler's count.
     */
    public static boolean metallumWritesDirectly(final int usage) {
        return (usage & GpuBuffer.USAGE_INDEX) != 0 || isSharedMemory(usage | GpuBuffer.USAGE_COPY_DST);
    }

    /** The Metal texture behind a view, or NULL for no view. For the extra color targets of a render pass. */
    public static MemorySegment nativeHandle(final @Nullable GpuTextureView view) {
        return view == null ? MemorySegment.NULL : ((MetalGpuTextureView) view).nativeHandle();
    }

    /** Draws a deferred clear of this texture now, the way Metallum does before a texture is used some other way. */
    public static void flushPendingClear(final Object commandEncoder, final GpuTexture texture) {
        ((MetalCommandEncoder) commandEncoder).flushPendingClear((MetalGpuTexture) texture);
    }

    /** Metallum calls this on every texture a render pass draws into, so a later clear is not skipped as redundant. */
    public static void markContentsDirty(final GpuTexture texture) {
        ((MetalGpuTexture) texture).markContentsDirty();
    }

    /** Which sampler slot each of a pipeline's textures uses in each stage; see {@link SamplerSlots}. */
    public static byte @Nullable [] samplerSlots(final Object compiledPipeline) {
        int[] vertex = new int[0];
        int[] fragment = new int[0];
        // Metallum lists a pipeline's resources in binding order, so these come out sorted.
        for (MetalCompiledRenderPipeline.ResourceBinding resource : ((MetalCompiledRenderPipeline) compiledPipeline).resources()) {
            if (resource.kind() != MetalCompiledRenderPipeline.ResourceKind.SAMPLED_IMAGE) continue;
            if ((resource.stageMask() & SamplerSlots.STAGE_VERTEX) != 0) vertex = append(vertex, resource.bindingIndex());
            if ((resource.stageMask() & SamplerSlots.STAGE_FRAGMENT) != 0) fragment = append(fragment, resource.bindingIndex());
        }
        return SamplerSlots.table(vertex, fragment);
    }

    private static int[] append(final int[] values, final int value) {
        int[] grown = Arrays.copyOf(values, values.length + 1);
        grown[values.length] = value;
        return grown;
    }
}
