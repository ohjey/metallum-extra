package com.metallumextra;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** In-game settings (opened from Mod Menu). The fix toggles take effect immediately and are saved to the config file. */
public final class ExtraConfigScreen extends Screen {
    private static final int ROW_WIDTH = 310;

    private final Screen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    public ExtraConfigScreen(final Screen parent) {
        super(Component.literal("Metallum Extra"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ExtraConfig config = ExtraConfig.get();
        layout.addTitleHeader(title, font);

        LinearLayout rows = layout.addToContents(LinearLayout.vertical().spacing(8));
        rows.addChild(toggle("Non-blocking present",
                "With vsync off, never stall a frame waiting for the display. Frames that finish while macOS has no "
                        + "swapchain image free are not shown (the next one is). No effect with vsync on.",
                config.nonBlockingPresent, config::setNonBlockingPresent));
        rows.addChild(toggle("Fast section re-centering",
                "Minecraft rescans every section slot in render distance each time you cross into a new chunk "
                        + "section. This updates only the ones that changed. Matters at very high render distances.",
                config.fastSectionRecenter, config::setFastSectionRecenter));
        rows.addChild(toggle("Direct buffer upload",
                "Fill CPU-visible buffers directly when they are created instead of breaking the frame for a GPU copy.",
                config.directBufferUpload, config::setDirectBufferUpload));
        rows.addChild(new MultiLineTextWidget(Component.literal(
                "Changes apply immediately. Profiler is " + (config.profilerEnabled ? "on" : "off")
                        + " (edit config/metallum-extra.properties and restart to change)."), font)
                .setMaxWidth(ROW_WIDTH).setCentered(true));

        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    private static CycleButton<Boolean> toggle(final String name, final String tooltip, final boolean value, final Consumer<Boolean> setter) {
        return CycleButton.onOffBuilder(value)
                .withTooltip(v -> Tooltip.create(Component.literal(tooltip)))
                .create(0, 0, ROW_WIDTH, 20, Component.literal(name), (button, v) -> setter.accept(v));
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
