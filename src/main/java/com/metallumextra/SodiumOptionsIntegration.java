package com.metallumextra;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.option.OptionFlag;
import net.caffeinemc.mods.sodium.api.config.option.OptionImpact;
import net.caffeinemc.mods.sodium.api.config.structure.BooleanOptionBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.OptionGroupBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Adds a Metallum Extra page to Sodium's video settings. Only loaded when Sodium is installed (it calls this entrypoint). */
public final class SodiumOptionsIntegration implements ConfigEntryPoint {
    @Override
    public void registerConfigLate(final ConfigBuilder builder) {
        builder.registerOwnModOptions()
                .addPage(builder.createOptionPage()
                        .setName(Component.literal("Metallum Extra"))
                        .addOptionGroup(shaderGroup(builder))
                        .addOptionGroup(group(builder, "Smoothness", Settings.smoothness()))
                        .addOptionGroup(group(builder, "Mod Compatibility", Settings.compatibility()))
                        .addOptionGroup(group(builder, "Troubleshooting", Settings.troubleshooting())));
    }

    private static OptionGroupBuilder shaderGroup(final ConfigBuilder builder) {
        OptionGroupBuilder group = group(builder, "Shaders", Settings.shaders());
        Settings.Choice<Quality> quality = Settings.shaderQuality();
        group.addOption(builder.createEnumOption(Identifier.fromNamespaceAndPath("metallum-extra", quality.id()), Quality.class)
                .setName(Component.literal(quality.name()))
                .setTooltip(Component.literal(quality.tooltip()))
                .setImpact(OptionImpact.valueOf(quality.impact().name()))
                .setDefaultValue(Quality.MEDIUM)
                .setElementNameProvider(value -> Component.literal(quality.label().apply(value)))
                .setBinding(quality.setter(), quality.getter())
                .setStorageHandler(() -> {
                }));
        return group;
    }

    private static OptionGroupBuilder group(final ConfigBuilder builder, final String name, final List<Settings.Toggle> toggles) {
        OptionGroupBuilder group = builder.createOptionGroup().setName(Component.literal(name));
        for (Settings.Toggle toggle : toggles) {
            BooleanOptionBuilder option = builder.createBooleanOption(Identifier.fromNamespaceAndPath("metallum-extra", toggle.id()))
                    .setName(Component.literal(toggle.name()))
                    .setTooltip(Component.literal(toggle.tooltip()))
                    .setImpact(OptionImpact.valueOf(toggle.impact().name()))
                    .setDefaultValue(toggle.defaultValue())
                    .setEnabled(toggle.available())
                    // The setters apply the change and write the config file themselves.
                    .setBinding(toggle.setter(), toggle.getter()::getAsBoolean)
                    .setStorageHandler(() -> {
                    });
            if (toggle.needsRestart()) {
                option.setFlags(OptionFlag.REQUIRES_GAME_RESTART);
            }
            group.addOption(option);
        }
        return group;
    }
}
