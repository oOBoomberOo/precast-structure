package io.github.ooboomberoo.precaststructure.registry;

import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(PrecastStructureMod.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeTabRegistry.create(
            Component.translatable("itemGroup.precast_structure.main"),
            () -> new ItemStack(ModItems.STRUCTURE_SCANNER.get())
    ));

    private ModCreativeTabs() {
    }

    public static void register() {
        TABS.register();
    }
}
