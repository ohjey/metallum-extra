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

/**
 * In-game settings (opened from Mod Menu). The same settings also appear in Sodium's video settings.
 * Smoothness toggles take effect immediately; everything is saved to the config file.
 */
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
        layout.addTitleHeader(title, font);

        LinearLayout rows = layout.addToContents(LinearLayout.vertical().spacing(6));
        for (Settings.Toggle toggle : Settings.smoothness()) {
            rows.addChild(button(toggle));
        }
        for (Settings.Toggle toggle : Settings.compatibility()) {
            rows.addChild(button(toggle));
        }
        for (Settings.Toggle toggle : Settings.troubleshooting()) {
            rows.addChild(button(toggle));
        }
        rows.addChild(new MultiLineTextWidget(Component.literal(
                "Hover a setting to see what it does. Changes apply right away unless marked (restart)."), font)
                .setMaxWidth(ROW_WIDTH).setCentered(true));

        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    private static CycleButton<Boolean> button(final Settings.Toggle toggle) {
        String name = toggle.needsRestart() ? toggle.name() + " (restart)" : toggle.name();
        return CycleButton.onOffBuilder(toggle.getter().getAsBoolean())
                .withTooltip(v -> Tooltip.create(Component.literal(toggle.tooltip())))
                .create(0, 0, ROW_WIDTH, 20, Component.literal(name), (button, v) -> toggle.setter().accept(v));
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
