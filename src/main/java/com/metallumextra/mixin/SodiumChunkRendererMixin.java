package com.metallumextra.mixin;

import com.metallumextra.FrameProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Profiler only: how often Sodium rebuilds a region's list of draws instead of reusing last frame's. A stored
 * draw list only pays off if the lists are reused for many frames.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class SodiumChunkRendererMixin {
    @Inject(method = "fillCommandBuffer", at = @At("HEAD"))
    private static void metallumExtra$listRebuilt(final CallbackInfo ci) {
        FrameProfiler.drawListRebuilt();
    }
}
