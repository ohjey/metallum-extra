package com.metallumextra;

import com.mojang.blaze3d.platform.InputConstants;
import com.metallumextra.shader.Shaders;
import com.metallumextra.shader.pack.PackManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.metallumextra.shader.pack.PackManager.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Three keys in the game's Controls, under Miscellaneous: reload the current shaders (R), switch shaders on and off (K)
 * and open the shader menu (I). They are added to the game's list of keys when its options are read (see
 * {@code OptionsKeysMixin}) and checked once a frame.
 */
public final class ShaderKeys {
    public static final KeyMapping RELOAD = new KeyMapping("key.metallum-extra.reload_shaders", InputConstants.KEY_R, KeyMapping.Category.MISC);
    public static final KeyMapping TOGGLE = new KeyMapping("key.metallum-extra.toggle_shaders", InputConstants.KEY_K, KeyMapping.Category.MISC);
    public static final KeyMapping OPEN = new KeyMapping("key.metallum-extra.open_shaders", InputConstants.KEY_I, KeyMapping.Category.MISC);

    private ShaderKeys() {
    }

    public static KeyMapping[] all() {
        return new KeyMapping[]{RELOAD, TOGGLE, OPEN};
    }

    /** Called at the start of every frame, on the render thread. */
    public static void tick(final Minecraft minecraft) {
        while (OPEN.consumeClick()) {
            if (minecraft.gui.screen() == null) minecraft.gui.setScreen(new ShaderPackScreen(null));
        }
        while (TOGGLE.consumeClick()) {
            String blocked = Shaders.unavailableReason();
            if (blocked != null) {
                say(minecraft, Component.literal("[Metallum Extra] Failed to toggle shaders! Reason: " + blocked).withStyle(ChatFormatting.RED));
                continue;
            }
            boolean on = !ExtraConfig.get().shadersEnabled;
            ExtraConfig.get().setShadersEnabled(on);
            Entry pack = PackManager.find(PackManager.activeId());
            say(minecraft, Component.literal(on
                    ? "[Metallum Extra] Toggled shaders to " + (pack == null ? "(unknown)" : pack.name()) + "!"
                    : "[Metallum Extra] Shaders disabled!"));
        }
        while (RELOAD.consumeClick()) {
            // Nothing to reload while shaders are off.
            if (!Shaders.active()) continue;
            // Reads the folder and the pack's ZIP again (a ZIP that changed on disk is taken in), then compiles again.
            PackManager.rescan();
            PackManager.select(PackManager.activeId());
            Shaders.reload();
            say(minecraft, Component.literal("[Metallum Extra] Shaders reloaded"));
        }
    }

    /** A line in the chat, as Iris does it, unless Shader Chat Messages is switched off (then nothing is ever said). */
    private static void say(final Minecraft minecraft, final Component message) {
        if (minecraft.player == null || !ExtraConfig.get().shaderMessages) return;
        minecraft.player.sendSystemMessage(message);
    }
}
