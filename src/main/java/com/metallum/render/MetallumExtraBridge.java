package com.metallum.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

/**
 * Lives in Metallum's package (inside the Metallum Extra jar) so it can reach Metallum's
 * package-private classes. Fabric loads all mods with one class loader, so this works at runtime.
 * Keep this class tiny: everything here depends on Metallum internals for one exact version.
 */
public final class MetallumExtraBridge {
    private MetallumExtraBridge() {
    }

    /**
     * Mirrors MetalDevice.createBuffer(label, usage, data) from newer Metallum source:
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
}
