package io.github.ooboomberoo.precaststructure.structure;

import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

public final class BlueprintItemData {
    private BlueprintItemData() {
    }

    public static void write(ItemStack stack, StructureBlueprint blueprint) {
        write(stack, blueprint, null);
    }

    public static void write(ItemStack stack, StructureBlueprint blueprint, @Nullable Component customName) {
        CompoundTag root = new CompoundTag();
        root.put(StructureBlueprint.ROOT_KEY, blueprint.save());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        stack.set(DataComponents.CUSTOM_NAME, customName != null ? customName : Component.translatable("item.precaststructure.blueprint.named", blueprint.size().getX(), blueprint.size().getY(), blueprint.size().getZ()));
    }

    public static Optional<StructureBlueprint> read(ItemStack stack, HolderLookup.Provider registries) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return Optional.empty();
        }

        CompoundTag root = data.copyTag();
        if (!root.contains(StructureBlueprint.ROOT_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            return Optional.empty();
        }
        return StructureBlueprint.load(root.getCompound(StructureBlueprint.ROOT_KEY), registries);
    }
}
