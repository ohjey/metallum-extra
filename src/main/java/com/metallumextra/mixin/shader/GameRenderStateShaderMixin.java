package com.metallumextra.mixin.shader;

import com.metallumextra.shader.Shaders;
import net.minecraft.client.renderer.state.GameRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shaders: the game's Improved Transparency setting draws water, particles, clouds and weather into images of their
 * own and merges them afterwards. Water here needs the world behind it in the image it is drawn into, so the
 * setting is treated as off while shaders are on. The player's setting itself is not touched.
 */
@Mixin(GameRenderState.class)
public abstract class GameRenderStateShaderMixin {
    @Inject(method = "useShaderTransparency", at = @At("HEAD"), cancellable = true)
    private void metallumExtra$plainTransparency(final CallbackInfoReturnable<Boolean> cir) {
        if (Shaders.active()) cir.setReturnValue(false);
    }
}
