package com.metallumextra.mixin;

import com.metallum.mtl.MetallumExtraPresent;
import com.metallumextra.FrameProfiler;
import com.mojang.blaze3d.systems.GpuSurface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame boundary: the end of presenting a frame. Also reports the present mode the game asked for. */
@Mixin(targets = "com.metallum.render.MetalSurface", remap = false)
public abstract class MetalSurfaceMixin {
    @Inject(method = "configure", at = @At("HEAD"))
    private void metallumExtra$configured(final GpuSurface.Configuration configuration, final CallbackInfo ci) {
        FrameProfiler.surfaceConfigured(configuration.presentMode(), configuration.width(), configuration.height());
        MetallumExtraPresent.setDisplaySyncOff(configuration.presentMode() == GpuSurface.PresentMode.MAILBOX);
    }

    @Inject(method = "present", at = @At("RETURN"))
    private void metallumExtra$frameEnd(final CallbackInfo ci) {
        FrameProfiler.frameEnd();
    }
}
