package com.metallumextra.mixin.shader;

import com.metallumextra.shader.Shaders;
import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shaders: the game darkens each block face by a fixed amount for the way it points (sides darker than the top)
 * and bakes that into the chunk mesh. With shaders on, how bright a face is comes from where the sun really is,
 * so the baked amount is made the same for every face. Chunks are rebuilt when shaders are switched.
 */
@Mixin(CardinalLighting.class)
public abstract class CardinalLightingShaderMixin {
    @Shadow
    public abstract float up();

    @Inject(method = "byFace", at = @At("HEAD"), cancellable = true)
    private void metallumExtra$sameForEveryFace(final Direction direction, final CallbackInfoReturnable<Float> cir) {
        if (Shaders.active()) cir.setReturnValue(this.up());
    }
}
