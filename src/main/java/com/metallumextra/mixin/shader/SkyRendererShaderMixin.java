package com.metallumextra.mixin.shader;

import com.metallumextra.shader.Shaders;
import com.metallumextra.shader.SkyPass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shaders: under an open sky the shader pipeline draws the sky itself, in place of the game's sky disc, its
 * sunrise fan and the dark disc it puts below the horizon. The sun, moon and stars stay the game's, drawn on
 * the tilted path the lighting uses.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererShaderMixin {
    @Shadow @Final private RenderTarget renderTarget;

    @Unique
    private boolean metallumExtra$tilted;

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void metallumExtra$ownSky(final int skyColor, final CallbackInfo ci) {
        if (Shaders.drawsSky()) {
            SkyPass.render(this.renderTarget);
            ci.cancel();
        }
    }

    /** The drawn sun, moon and stars follow the tilted path the lighting uses. */
    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"))
    private void metallumExtra$tiltSunPath(final PoseStack poseStack, final float sunAngle, final float moonAngle, final float starAngle,
                                           final MoonPhase moonPhase, final float rainBrightness, final float starBrightness, final CallbackInfo ci) {
        this.metallumExtra$tilted = Shaders.active() && Shaders.phase() == Shaders.PHASE_WORLD;
        if (this.metallumExtra$tilted) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotation(Shaders.sunPathTilt()));
        }
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("RETURN"))
    private void metallumExtra$untiltSunPath(final PoseStack poseStack, final float sunAngle, final float moonAngle, final float starAngle,
                                             final MoonPhase moonPhase, final float rainBrightness, final float starBrightness, final CallbackInfo ci) {
        if (this.metallumExtra$tilted) {
            poseStack.popPose();
            this.metallumExtra$tilted = false;
        }
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void metallumExtra$noSunriseFan(final CallbackInfo ci) {
        if (Shaders.drawsSky()) ci.cancel();
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void metallumExtra$noDarkDisc(final CallbackInfo ci) {
        if (Shaders.drawsSky()) ci.cancel();
    }
}
