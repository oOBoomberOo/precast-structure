package io.github.ooboomberoo.precaststructure.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config screen used by Mod Menu (Fabric) and the NeoForge mods list via Architectury.
 */
public final class ModConfigScreen {
    private ModConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ModConfig config = ModConfig.get();
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("config.precast_structure.title"))
            .setSavingRunnable(ModConfig::save);

        ConfigEntryBuilder entries = builder.entryBuilder();

        ConfigCategory scanning = builder.getOrCreateCategory(Component.translatable("config.precast_structure.category.scanning"));
        scanning.addEntry(entries.startIntField(Component.translatable("config.precast_structure.scanning.min_ticks"), config.scanning.minTicks)
            .setDefaultValue(80)
            .setMin(1)
            .setMax(20_000)
            .setTooltip(Component.translatable("config.precast_structure.scanning.min_ticks.tooltip"))
            .setSaveConsumer(value -> ModConfig.get().scanning.minTicks = value)
            .build());
        scanning.addEntry(entries.startIntField(Component.translatable("config.precast_structure.scanning.ticks_per_height"), config.scanning.ticksPerHeight)
            .setDefaultValue(16)
            .setMin(0)
            .setMax(200)
            .setTooltip(Component.translatable("config.precast_structure.scanning.ticks_per_height.tooltip"))
            .setSaveConsumer(value -> ModConfig.get().scanning.ticksPerHeight = value)
            .build());
        scanning.addEntry(entries.startIntField(Component.translatable("config.precast_structure.scanning.sound_interval"), config.scanning.soundIntervalTicks)
            .setDefaultValue(5)
            .setMin(1)
            .setMax(200)
            .setTooltip(Component.translatable("config.precast_structure.scanning.sound_interval.tooltip"))
            .setSaveConsumer(value -> ModConfig.get().scanning.soundIntervalTicks = value)
            .build());

        ConfigCategory deploy = builder.getOrCreateCategory(Component.translatable("config.precast_structure.category.deploy"));
        deploy.addEntry(entries.startBooleanToggle(Component.translatable("config.precast_structure.deploy.animated"), config.deploy.animated)
            .setDefaultValue(true)
            .setTooltip(Component.translatable("config.precast_structure.deploy.animated.tooltip"))
            .setSaveConsumer(value -> ModConfig.get().deploy.animated = value)
            .build());
        deploy.addEntry(entries.startIntField(Component.translatable("config.precast_structure.deploy.min_ticks"), config.deploy.minTicks)
            .setDefaultValue(80)
            .setMin(1)
            .setMax(20_000)
            .setTooltip(Component.translatable("config.precast_structure.deploy.min_ticks.tooltip"))
            .setSaveConsumer(value -> ModConfig.get().deploy.minTicks = value)
            .build());
        deploy.addEntry(entries.startIntField(Component.translatable("config.precast_structure.deploy.ticks_per_height"), config.deploy.ticksPerHeight)
            .setDefaultValue(16)
            .setMin(0)
            .setMax(200)
            .setTooltip(Component.translatable("config.precast_structure.deploy.ticks_per_height.tooltip"))
            .setSaveConsumer(value -> ModConfig.get().deploy.ticksPerHeight = value)
            .build());
        deploy.addEntry(entries.startIntField(Component.translatable("config.precast_structure.deploy.sound_interval"), config.deploy.soundIntervalTicks)
            .setDefaultValue(5)
            .setMin(1)
            .setMax(200)
            .setTooltip(Component.translatable("config.precast_structure.deploy.sound_interval.tooltip"))
            .setSaveConsumer(value -> ModConfig.get().deploy.soundIntervalTicks = value)
            .build());
        deploy.addEntry(entries.startIntField(Component.translatable("config.precast_structure.deploy.client_grace_ticks"), config.deploy.clientGraceTicks)
            .setDefaultValue(8)
            .setMin(0)
            .setMax(100)
            .setTooltip(Component.translatable("config.precast_structure.deploy.client_grace_ticks.tooltip"))
            .setSaveConsumer(value -> ModConfig.get().deploy.clientGraceTicks = value)
            .build());

        ConfigCategory frame = builder.getOrCreateCategory(Component.translatable("config.precast_structure.category.frame"));
        frame.addEntry(entries.startIntField(Component.translatable("config.precast_structure.frame.min_platform_size"), config.frame.minPlatformSize)
            .setDefaultValue(3)
            .setMin(1)
            .setMax(256)
            .setTooltip(Component.translatable("config.precast_structure.frame.min_platform_size.tooltip"))
            .setSaveConsumer(value -> ModConfig.get().frame.minPlatformSize = value)
            .build());
        frame.addEntry(entries.startIntField(Component.translatable("config.precast_structure.frame.max_platform_size"), config.frame.maxPlatformSize)
            .setDefaultValue(64)
            .setMin(config.frame.minPlatformSize)
            .setMax(256)
            .setTooltip(Component.translatable("config.precast_structure.frame.max_platform_size.tooltip"))
            .setSaveConsumer(value -> ModConfig.get().frame.maxPlatformSize = value)
            .build());

        ConfigCategory blueprint = builder.getOrCreateCategory(Component.translatable("config.precast_structure.category.blueprint"));
        blueprint.addEntry(entries.startIntField(Component.translatable("config.precast_structure.blueprint.max_dimension"), config.blueprint.maxDimension)
            .setDefaultValue(256)
            .setMin(1)
            .setMax(512)
            .setTooltip(Component.translatable("config.precast_structure.blueprint.max_dimension.tooltip"))
            .setSaveConsumer(value -> ModConfig.get().blueprint.maxDimension = value)
            .build());

        ConfigCategory printer = builder.getOrCreateCategory(Component.translatable("config.precast_structure.category.printer"));
        printer.addEntry(entries.startIntField(Component.translatable("config.precast_structure.printer.default_delay_ticks"), config.printer.defaultDelayTicks)
            .setDefaultValue(100)
            .setMin(1)
            .setMax(20_000)
            .setTooltip(Component.translatable("config.precast_structure.printer.default_delay_ticks.tooltip"))
            .setSaveConsumer(value -> ModConfig.get().printer.defaultDelayTicks = value)
            .build());

        return builder.build();
    }
}
