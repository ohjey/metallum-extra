package com.metallumextra.mixin.shader;

import com.llamalad7.mixinextras.sugar.Local;
import com.metallumextra.shader.Shaders;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shaders: tells the shader pipeline where the frame is, since its replaced shaders draw menus and the world alike. */
@Mixin(GameRenderer.class)
public abstract class GameRendererShaderMixin {
    @Unique
    private static final String DRAW_LEVEL = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V";

    @Inject(method = "render", at = @At("HEAD"))
    private void metallumExtra$beginFrame(final DeltaTracker deltaTracker, final boolean advanceGameTime, final CallbackInfo ci) {
        Shaders.beginFrame();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = DRAW_LEVEL))
    private void metallumExtra$beginWorld(final DeltaTracker deltaTracker, final CallbackInfo ci, @Local final Matrix4f projectionMatrix) {
        Shaders.beginWorld(projectionMatrix);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = DRAW_LEVEL, shift = At.Shift.AFTER))
    private void metallumExtra$afterWorld(final DeltaTracker deltaTracker, final CallbackInfo ci) {
        Shaders.afterWorld();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void metallumExtra$endWorld(final DeltaTracker deltaTracker, final CallbackInfo ci) {
        Shaders.endWorld();
    }
}
