package com.metallumextra.mixin;

import com.metallumextra.ExtraConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Compatibility: Shine on Metallum. This one is in Shine (and does nothing without it).
 * <p>
 * Shine 3.1 works out its graphics backend by looking for "vulkan" or "opengl" in the game's backend name. On
 * Metallum the name is "Metal", so Shine settles on "unknown": it skips its raw OpenGL calls, but it also skips
 * the Vulkan-mode code that supplies its terrain shader's data through the game's own rendering API. That
 * Vulkan-mode code has nothing Vulkan-specific in it, so here "Metal" is answered the same way as "Vulkan".
 * Shine also draws into several color targets at once and reads many textures in one shader, which need
 * {@code compat.multipleRenderTargets} and {@code compat.manyTextures}.
 */
@Pseudo
@Mixin(targets = "com.bloom.client.render.ShineRenderBackend", remap = false)
public abstract class ShineBackendMixin {
    @Redirect(method = "identify",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;contains(Ljava/lang/CharSequence;)Z"))
    private static boolean metallumExtra$treatMetalLikeVulkan(final String name, final CharSequence wanted) {
        if (name.contains(wanted)) return true;
        return ExtraConfig.get().shineSupport && "vulkan".contentEquals(wanted) && name.equals("metal");
    }
}
