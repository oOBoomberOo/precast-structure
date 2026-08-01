package io.github.ooboomberoo.precaststructure.structure;

import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class BlueprintItemData {
    private BlueprintItemData() {
    }

    public static void write(ItemStack stack, StructureBlueprint blueprint) {
        write(stack, blueprint, null);
    }

    public static void write(ItemStack stack, StructureBlueprint blueprint, @Nullable Component customName) {
        CompoundTag root = stack.getOrCreateTag();
        root.put(StructureBlueprint.ROOT_KEY, blueprint.save());
        stack.setHoverName(customName != null
            ? customName
            : Component.translatable(
                "item.precast_structure.blueprint.named",
                blueprint.size().getX(),
                blueprint.size().getY(),
                blueprint.size().getZ()
            ));
    }

    public static Optional<StructureBlueprint> read(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(StructureBlueprint.ROOT_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        return StructureBlueprint.load(root.getCompound(StructureBlueprint.ROOT_KEY), registries);
    }

    public static boolean hasStructure(ItemStack stack) {
        CompoundTag root = stack.getTag();
        return root != null && root.contains(StructureBlueprint.ROOT_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND);
    }
}
