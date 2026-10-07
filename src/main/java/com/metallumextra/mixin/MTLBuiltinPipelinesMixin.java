package com.metallumextra.mixin;

import com.metallum.mtl.CAMetalDrawable;
import com.metallum.mtl.CAMetalLayer;
import com.metallum.mtl.MetallumExtraPresent;
import com.metallumextra.ExtraConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Fix: never block the render thread waiting for a swapchain image when vsync is off. */
@Mixin(targets = "com.metallum.mtl.MTLBuiltinPipelines", remap = false)
public abstract class MTLBuiltinPipelinesMixin {
    @Redirect(method = "encodePresentTextureToDrawable",
            at = @At(value = "INVOKE", target = "Lcom/metallum/mtl/CAMetalLayer;nextDrawable()Lcom/metallum/mtl/CAMetalDrawable;"))
    private static CAMetalDrawable metallumExtra$nextDrawable(final CAMetalLayer layer) {
        return MetallumExtraPresent.nextDrawable(layer);
    }
}
