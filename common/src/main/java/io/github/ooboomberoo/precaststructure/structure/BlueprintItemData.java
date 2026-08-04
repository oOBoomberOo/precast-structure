package io.github.ooboomberoo.precaststructure.structure;

import io.github.ooboomberoo.precaststructure.registry.ModDataComponents;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

public final class BlueprintItemData {
  private BlueprintItemData() {}

  public static void write(ItemStack stack, StructureBlueprint blueprint) {
    write(stack, blueprint, null);
  }

  public static void write(
      ItemStack stack, StructureBlueprint blueprint, @Nullable Component customName) {
    // Persist a tight content AABB: scan/ghost keep frame-relative offsets until this write.
    StructureBlueprint stored = blueprint.trimmedToContents();
    stack.set(ModDataComponents.BLUEPRINT_STRUCTURE.get(), stored);
    stripLegacyCustomData(stack);
    if (customName != null) {
      stack.set(DataComponents.CUSTOM_NAME, customName);
      return;
    }
    BlockPos contentSize = stored.size();
    stack.set(
        DataComponents.CUSTOM_NAME,
        Component.translatable(
            "item.precast_structure.blueprint.named",
            contentSize.getX(),
            contentSize.getY(),
            contentSize.getZ()));
  }

  public static Optional<StructureBlueprint> read(
      ItemStack stack, HolderLookup.Provider registries) {
    StructureBlueprint structure = stack.get(ModDataComponents.BLUEPRINT_STRUCTURE.get());
    if (structure != null) {
      return Optional.of(structure);
    }

    Optional<StructureBlueprint> legacy = readLegacyCustomData(stack, registries);
    legacy.ifPresent(blueprint -> write(stack, blueprint, stack.get(DataComponents.CUSTOM_NAME)));
    return legacy;
  }

  public static boolean hasStructure(ItemStack stack) {
    if (stack.get(ModDataComponents.BLUEPRINT_STRUCTURE.get()) != null) {
      return true;
    }
    return readLegacyCustomData(stack, null).isPresent();
  }

  /**
   * Migrates legacy {@code custom_data.PrecastStructure} onto {@link
   * ModDataComponents#BLUEPRINT_STRUCTURE} for every stack in the inventory.
   */
  public static void migrateInventory(Inventory inventory) {
    for (int i = 0; i < inventory.getContainerSize(); i++) {
      migrateStack(inventory.getItem(i));
    }
  }

  public static void migrateStack(ItemStack stack) {
    if (stack.isEmpty()) {
      return;
    }
    if (!stack.is(ModItems.BLUEPRINT.get()) && !stack.is(ModItems.PRECAST_STRUCTURE.get())) {
      return;
    }
    if (stack.get(ModDataComponents.BLUEPRINT_STRUCTURE.get()) != null) {
      stripLegacyCustomData(stack);
      return;
    }
    readLegacyCustomData(stack, null)
        .ifPresent(blueprint -> write(stack, blueprint, stack.get(DataComponents.CUSTOM_NAME)));
  }

  private static Optional<StructureBlueprint> readLegacyCustomData(
      ItemStack stack, @Nullable HolderLookup.Provider registries) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    if (data == null) {
      return Optional.empty();
    }
    CompoundTag root = data.copyTag();
    if (!root.contains(StructureBlueprint.ROOT_KEY, Tag.TAG_COMPOUND)) {
      return Optional.empty();
    }
    CompoundTag structure = root.getCompound(StructureBlueprint.ROOT_KEY);
    return registries != null
        ? StructureBlueprint.load(structure, registries)
        : StructureBlueprint.load(structure);
  }

  private static void stripLegacyCustomData(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    if (data == null) {
      return;
    }
    CompoundTag tag = data.copyTag();
    if (!tag.contains(StructureBlueprint.ROOT_KEY)) {
      return;
    }
    tag.remove(StructureBlueprint.ROOT_KEY);
    if (tag.isEmpty()) {
      stack.remove(DataComponents.CUSTOM_DATA);
    } else {
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
  }
}
