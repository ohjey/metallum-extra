package com.metallumextra;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** How the shaders look and what they cost: the quality preset and each effect. Every change applies right away. */
public final class ShaderOptionsScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;

    private final Screen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    public ShaderOptionsScreen(final Screen parent) {
        super(Component.literal("Shader Options"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);

        LinearLayout rows = layout.addToContents(LinearLayout.vertical().spacing(6));
        GridLayout grid = rows.addChild(new GridLayout());
        grid.defaultCellSetting().padding(3);
        GridLayout.RowHelper cells = grid.createRowHelper(2);
        cells.addChild(ExtraConfigScreen.choice(Settings.shaderQuality()));
        for (Settings.Toggle toggle : Settings.shaders()) {
            cells.addChild(ExtraConfigScreen.button(toggle));
        }

        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(BUTTON_WIDTH).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
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
