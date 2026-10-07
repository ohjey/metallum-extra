package com.metallumextra.mixin;

import com.metallumextra.ExtraConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Compatibility: Distant Horizons on Metallum. This one is in Distant Horizons (and does nothing without it).
 * <p>
 * DH 3.3.x asks the game for its graphics backend name and treats anything that is not "Vulkan" as OpenGL. On
 * Metallum the name is "Metal", so DH takes its OpenGL path and crashes casting Metal textures to OpenGL ones.
 * DH's Vulkan path is written against the game's own rendering API with nothing Vulkan-specific in it, so here
 * "Metal" is answered the same way as "Vulkan".
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper", remap = false)
public abstract class DistantHorizonsBackendMixin {
    @Redirect(method = "getMcRenderingApi",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;equalsIgnoreCase(Ljava/lang/String;)Z"))
    private boolean metallumExtra$treatMetalLikeVulkan(final String backendName, final String expected) {
        if (backendName.equalsIgnoreCase(expected)) return true;
        return ExtraConfig.get().distantHorizonsSupport && "Metal".equalsIgnoreCase(backendName);
    }
}
