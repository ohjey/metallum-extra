package com.metallumextra.mixin;

import com.metallumextra.ShaderKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Adds the shader keys to the game's list of keys, before the options file is read so that a key the player has
 * changed is restored. (Fabric API's key helper would do this, but this mod does not need Fabric API.)
 */
@Mixin(Options.class)
public abstract class OptionsKeysMixin {
    @Mutable
    @Final
    @Shadow
    public KeyMapping[] keyMappings;

    @Inject(method = "load", at = @At("HEAD"))
    private void metallumExtra$addShaderKeys(final CallbackInfo ci) {
        if (Arrays.asList(keyMappings).contains(ShaderKeys.RELOAD)) return;
        KeyMapping[] ours = ShaderKeys.all();
        KeyMapping[] all = Arrays.copyOf(keyMappings, keyMappings.length + ours.length);
        System.arraycopy(ours, 0, all, keyMappings.length, ours.length);
        keyMappings = all;
    }
}
