package com.metallumextra.mixin;

import com.metallumextra.ExtraConfig;
import com.metallumextra.FrameProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;

/**
 * Fix: spread Sodium's buffer cleanup. This one is in Sodium, not Metallum (and does nothing without Sodium).
 * <p>
 * Every frame Sodium empties a queue of chunk-mesh buffers the garbage collector has finished with. The queue only
 * fills when a collection cycle ends, so after one it can hold a few hundred thousand entries (thousands of meshes
 * are built per second while chunks stream in) and emptying it all at once takes 50-130 ms on the render thread.
 * Here each frame only works on the queue for a short time and leaves the rest for the following frames. Nothing
 * is skipped: entries simply wait in the queue. Sodium's emergency path (reclaim(true)) is left alone.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.util.NativeBuffer", remap = false)
public abstract class SodiumNativeBufferMixin {
    @Unique
    private static final ExtraConfig metallumExtra$CONFIG = ExtraConfig.get();
    @Unique
    private static final long metallumExtra$BUDGET_NS = 300_000L;
    @Unique
    private static long metallumExtra$deadline;

    @Inject(method = "reclaim(Z)V", at = @At("HEAD"))
    private static void metallumExtra$startBudget(final boolean forceGc, final CallbackInfo ci) {
        metallumExtra$deadline = System.nanoTime() + metallumExtra$BUDGET_NS;
    }

    @Redirect(method = "reclaim(Z)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/ref/ReferenceQueue;poll()Ljava/lang/ref/Reference;"))
    private static Reference<?> metallumExtra$pollWithinBudget(final ReferenceQueue<?> queue, final boolean forceGc) {
        if (!forceGc && metallumExtra$CONFIG.spreadSodiumCleanup && System.nanoTime() > metallumExtra$deadline) {
            FrameProfiler.cleanupDeferred();
            return null;
        }
        return queue.poll();
    }
}
