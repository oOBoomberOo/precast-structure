package io.github.ooboomberoo.precaststructure.structure.special;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Targeted container-content stripping for blueprint replicas.
 * Removes item lists / loot table refs while leaving other BE fields (Create machine config,
 * brackets, custom names, etc.) intact.
 */
public final class InventoryNbt {
    private static final String[] LIST_OR_COMPOUND_KEYS = {
        "Items",
        "Inventory",
        "item",
        "Item",
        "RecordItem",
        "Book",
        "item_stack"
    };

    private static final String[] LOOT_KEYS = {
        "LootTable",
        "loot_table",
        "LootTableSeed",
        "loot_table_seed"
    };

    private InventoryNbt() {
    }

    @Nullable
    public static CompoundTag stripContainerContents(@Nullable CompoundTag nbt) {
        if (nbt == null || nbt.isEmpty()) {
            return nbt;
        }
        CompoundTag copy = nbt.copy();
        for (String key : LIST_OR_COMPOUND_KEYS) {
            copy.remove(key);
        }
        for (String key : LOOT_KEYS) {
            copy.remove(key);
        }
        return copy.isEmpty() ? null : copy;
    }
}
