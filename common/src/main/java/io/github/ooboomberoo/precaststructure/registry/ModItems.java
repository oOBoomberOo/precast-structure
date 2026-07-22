package io.github.ooboomberoo.precaststructure.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.item.BlueprintItem;
import io.github.ooboomberoo.precaststructure.PlatformItemFactory;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(PrecastStructureMod.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> PLATFORM_FLOOR = ITEMS.register("platform_floor", () -> new BlockItem(ModBlocks.PLATFORM_FLOOR.get(), new Item.Properties()));
    public static final RegistrySupplier<Item> PERIMETER_FENCE = ITEMS.register("perimeter_fence", () -> new BlockItem(ModBlocks.PERIMETER_FENCE.get(), new Item.Properties()));
    public static final RegistrySupplier<Item> METAL_SCAFFOLD = ITEMS.register("metal_scaffold", () -> new BlockItem(ModBlocks.METAL_SCAFFOLD.get(), new Item.Properties()));
    public static final RegistrySupplier<Item> STRUCTURE_SCANNER = ITEMS.register("structure_scanner", () -> new BlockItem(ModBlocks.STRUCTURE_SCANNER.get(), new Item.Properties().stacksTo(16)));
    public static final RegistrySupplier<Item> STRUCTURE_PRINTER = ITEMS.register("structure_printer", () -> new BlockItem(ModBlocks.STRUCTURE_PRINTER.get(), new Item.Properties().stacksTo(16)));
    public static final RegistrySupplier<Item> BLUEPRINT = ITEMS.register("blueprint", () -> new BlueprintItem(new Item.Properties().stacksTo(1)));
    public static final RegistrySupplier<Item> PRECAST_STRUCTURE = ITEMS.register("precast_structure", () -> PlatformItemFactory.createPrecastStructureItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }

    public static void register() {
        ITEMS.register();
    }
}
