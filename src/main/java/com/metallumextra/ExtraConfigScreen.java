package com.metallumextra;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * In-game settings (opened from Mod Menu). The same settings also appear in Sodium's video settings.
 * Shader and smoothness toggles take effect immediately; everything is saved to the config file.
 */
public final class ExtraConfigScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int TEXT_WIDTH = 390;

    private final Screen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    public ExtraConfigScreen(final Screen parent) {
        super(Component.literal("Metallum Extra"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);

        // Two columns, so every setting fits on screen at any GUI scale.
        LinearLayout rows = layout.addToContents(LinearLayout.vertical().spacing(6));
        GridLayout grid = rows.addChild(new GridLayout());
        grid.defaultCellSetting().padding(3);
        GridLayout.RowHelper cells = grid.createRowHelper(2);
        cells.addChild(Button.builder(Component.literal("Shaders..."), button -> minecraft.gui.setScreen(new ShaderPackScreen(this)))
                .width(BUTTON_WIDTH).tooltip(Tooltip.create(Component.literal("Choose a shader pack, switch shaders on or off, and set how they look."))).build());
        for (Settings.Toggle toggle : Settings.smoothness()) {
            cells.addChild(button(toggle));
        }
        for (Settings.Toggle toggle : Settings.messages()) {
            cells.addChild(button(toggle));
        }
        for (Settings.Toggle toggle : Settings.compatibility()) {
            cells.addChild(button(toggle));
        }
        for (Settings.Toggle toggle : Settings.troubleshooting()) {
            cells.addChild(button(toggle));
        }
        rows.addChild(new MultiLineTextWidget(Component.literal(
                "Hover a setting to see what it does. Changes apply right away; those marked * need a restart."), font)
                .setMaxWidth(TEXT_WIDTH).setCentered(true));

        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    static CycleButton<Boolean> button(final Settings.Toggle toggle) {
        String name = toggle.needsRestart() ? toggle.name() + "*" : toggle.name();
        CycleButton<Boolean> button = CycleButton.onOffBuilder(toggle.getter().getAsBoolean())
                .withTooltip(v -> Tooltip.create(Component.literal(toggle.tooltip())))
                .create(0, 0, BUTTON_WIDTH, 20, Component.literal(name), (widget, v) -> toggle.setter().accept(v));
        button.active = toggle.available();
        return button;
    }

    static <E extends Enum<E>> CycleButton<E> choice(final Settings.Choice<E> choice) {
        return CycleButton.<E>builder(value -> Component.literal(choice.label().apply(value)), choice.getter())
                .withValues(choice.type().getEnumConstants())
                .withTooltip(v -> Tooltip.create(Component.literal(choice.tooltip())))
                .create(0, 0, BUTTON_WIDTH, 20, Component.literal(choice.name()), (widget, v) -> choice.setter().accept(v));
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
