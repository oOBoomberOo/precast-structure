package io.github.ooboomberoo.precaststructure.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
        DeferredRegister.create(PrecastStructureMod.MOD_ID, Registries.DATA_COMPONENT_TYPE);

    /**
     * Typed blueprint payload (size + blocks). Replaces storing under {@code minecraft:custom_data}.
     */
    public static final RegistrySupplier<DataComponentType<StructureBlueprint>> BLUEPRINT_STRUCTURE =
        DATA_COMPONENTS.register(
            "blueprint_structure",
            () -> DataComponentType.<StructureBlueprint>builder()
                .persistent(StructureBlueprint.CODEC)
                .networkSynchronized(StructureBlueprint.STREAM_CODEC)
                .build()
        );

    private ModDataComponents() {
    }

    public static void register() {
        DATA_COMPONENTS.register();
    }
}
