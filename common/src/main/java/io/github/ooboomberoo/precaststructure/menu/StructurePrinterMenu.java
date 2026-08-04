package io.github.ooboomberoo.precaststructure.menu;

import io.github.ooboomberoo.precaststructure.block.entity.StructurePrinterBlockEntity;
import io.github.ooboomberoo.precaststructure.registry.ModBlocks;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import io.github.ooboomberoo.precaststructure.registry.ModMenuTypes;
import io.github.ooboomberoo.precaststructure.structure.BlueprintItemData;
import io.github.ooboomberoo.precaststructure.structure.MaterialRequirement;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class StructurePrinterMenu extends AbstractContainerMenu {
  public static final int BLUEPRINT_SLOT_X = 26;
  public static final int BLUEPRINT_SLOT_Y = 35;
  public static final int OUTPUT_SLOT_X = 152;
  public static final int OUTPUT_SLOT_Y = 35;
  public static final int MATERIAL_SLOT_X = 53;
  public static final int MATERIAL_SLOT_Y = 18;
  public static final int MATERIAL_COLUMNS = 4;
  public static final int VISIBLE_MATERIAL_ROWS = 3;
  public static final int VISIBLE_MATERIAL_SLOTS = MATERIAL_COLUMNS * VISIBLE_MATERIAL_ROWS;
  public static final int SLOT_SPACING = 18;
  // 24px furnace arrow; tip at +22 must stop before output slot art (OUTPUT_SLOT_X - 1).
  public static final int PROGRESS_X = 127;
  public static final int PROGRESS_Y = 35;
  public static final int PROGRESS_WIDTH = 24;
  public static final int PROGRESS_HEIGHT = 16;
  public static final int SCROLLBAR_X = 125;
  public static final int SCROLLBAR_Y = MATERIAL_SLOT_Y;
  public static final int SCROLLBAR_WIDTH = 6;
  public static final int SCROLLBAR_HEIGHT = VISIBLE_MATERIAL_ROWS * SLOT_SPACING;
  public static final int IMAGE_HEIGHT = 166;
  public static final int INVENTORY_LABEL_Y = 72;
  public static final int PLAYER_INVENTORY_Y = 84;
  public static final int HOTBAR_Y = 142;

  private static final int CONTAINER_SLOT_COUNT = StructurePrinterBlockEntity.SLOT_COUNT;
  private static final int PLAYER_INVENTORY_START = CONTAINER_SLOT_COUNT;
  private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
  private static final int HOTBAR_START = PLAYER_INVENTORY_END;
  private static final int HOTBAR_END = HOTBAR_START + 9;

  private final Container container;
  private final ContainerLevelAccess access;
  private final ContainerData progressData;
  private final HolderLookup.Provider registries;

  private int scrollRow;

  public StructurePrinterMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
    this(
        containerId,
        inventory,
        new SimpleContainer(CONTAINER_SLOT_COUNT),
        new SimpleContainerData(StructurePrinterBlockEntity.DATA_COUNT),
        ContainerLevelAccess.create(inventory.player.level(), buf.readBlockPos()),
        inventory.player.level().registryAccess());
  }

  public StructurePrinterMenu(
      int containerId, Inventory inventory, StructurePrinterBlockEntity blockEntity) {
    this(
        containerId,
        inventory,
        blockEntity,
        blockEntity.getProgressData(),
        ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
        blockEntity.getLevel().registryAccess());
  }

  private StructurePrinterMenu(
      int containerId,
      Inventory inventory,
      Container container,
      ContainerData progressData,
      ContainerLevelAccess access,
      HolderLookup.Provider registries) {
    super(ModMenuTypes.STRUCTURE_PRINTER.get(), containerId);
    checkContainerSize(container, CONTAINER_SLOT_COUNT);
    checkContainerDataCount(progressData, StructurePrinterBlockEntity.DATA_COUNT);
    this.container = container;
    this.progressData = progressData;
    this.access = access;
    this.registries = registries;
    container.startOpen(inventory.player);
    this.addDataSlots(progressData);

    this.addSlot(
        new Slot(
            container,
            StructurePrinterBlockEntity.BLUEPRINT_SLOT,
            BLUEPRINT_SLOT_X,
            BLUEPRINT_SLOT_Y) {
          @Override
          public boolean mayPlace(ItemStack stack) {
            return stack.is(ModItems.BLUEPRINT.get()) && BlueprintItemData.hasStructure(stack);
          }
        });

    for (int i = 0; i < StructurePrinterBlockEntity.MATERIAL_SLOT_COUNT; i++) {
      int row = i / MATERIAL_COLUMNS;
      int column = i % MATERIAL_COLUMNS;
      this.addSlot(
          new MaterialSlot(
              container,
              StructurePrinterBlockEntity.FIRST_MATERIAL_SLOT + i,
              MATERIAL_SLOT_X + column * SLOT_SPACING,
              MATERIAL_SLOT_Y + row * SLOT_SPACING,
              i));
    }

    this.addSlot(
        new Slot(container, StructurePrinterBlockEntity.OUTPUT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
          @Override
          public boolean mayPlace(ItemStack stack) {
            return false;
          }
        });

    for (int row = 0; row < 3; row++) {
      for (int column = 0; column < 9; column++) {
        this.addSlot(
            new Slot(
                inventory, column + row * 9 + 9, 8 + column * 18, PLAYER_INVENTORY_Y + row * 18));
      }
    }

    for (int column = 0; column < 9; column++) {
      this.addSlot(new Slot(inventory, column, 8 + column * 18, HOTBAR_Y));
    }

    refreshLayout();
  }

  public void refreshLayout() {
    clampScroll();
    int firstVisible = scrollRow * MATERIAL_COLUMNS;
    int lastVisible = firstVisible + VISIBLE_MATERIAL_SLOTS;

    for (int i = 0; i < StructurePrinterBlockEntity.MATERIAL_SLOT_COUNT; i++) {
      Slot slot = slots.get(StructurePrinterBlockEntity.FIRST_MATERIAL_SLOT + i);
      if (i >= firstVisible && i < lastVisible && isMaterialSlotPresent(i)) {
        int local = i - firstVisible;
        int row = local / MATERIAL_COLUMNS;
        int column = local % MATERIAL_COLUMNS;
        SlotLayout.set(
            slot, MATERIAL_SLOT_X + column * SLOT_SPACING, MATERIAL_SLOT_Y + row * SLOT_SPACING);
      } else {
        SlotLayout.set(slot, -9999, -9999);
      }
    }
  }

  private boolean isMaterialSlotPresent(int materialIndex) {
    return getMaterialRequirement(materialIndex) != null
        || !container
            .getItem(StructurePrinterBlockEntity.FIRST_MATERIAL_SLOT + materialIndex)
            .isEmpty();
  }

  public int getMaterialListSize() {
    int count = 0;
    for (int i = 0; i < StructurePrinterBlockEntity.MATERIAL_SLOT_COUNT; i++) {
      if (isMaterialSlotPresent(i)) {
        count = i + 1;
      }
    }
    return count;
  }

  public int getMaxScrollRow() {
    int totalRows = (getMaterialListSize() + MATERIAL_COLUMNS - 1) / MATERIAL_COLUMNS;
    return Math.max(0, totalRows - VISIBLE_MATERIAL_ROWS);
  }

  public boolean canScroll() {
    return getMaxScrollRow() > 0;
  }

  public int getScrollRow() {
    return scrollRow;
  }

  public void setScrollRow(int row) {
    int clamped = Mth.clamp(row, 0, getMaxScrollRow());
    if (clamped != scrollRow) {
      scrollRow = clamped;
      refreshLayout();
    } else {
      clampScroll();
      refreshLayout();
    }
  }

  public void scroll(int deltaRows) {
    setScrollRow(scrollRow + deltaRows);
  }

  private void clampScroll() {
    scrollRow = Mth.clamp(scrollRow, 0, getMaxScrollRow());
  }

  public float getScrollProgress() {
    int max = getMaxScrollRow();
    return max == 0 ? 0.0F : scrollRow / (float) max;
  }

  public List<MaterialRequirement> getMaterialRequirements() {
    ItemStack blueprintStack = container.getItem(StructurePrinterBlockEntity.BLUEPRINT_SLOT);
    if (!blueprintStack.is(ModItems.BLUEPRINT.get())) {
      return List.of();
    }
    return BlueprintItemData.read(blueprintStack, registries)
        .map(blueprint -> blueprint.materialSlotRequirements(registries))
        .orElse(List.of());
  }

  @Nullable
  public MaterialRequirement getMaterialRequirement(int materialIndex) {
    List<MaterialRequirement> requirements = getMaterialRequirements();
    if (materialIndex < 0
        || materialIndex >= requirements.size()
        || materialIndex >= StructurePrinterBlockEntity.MATERIAL_SLOT_COUNT) {
      return null;
    }
    return requirements.get(materialIndex);
  }

  public int getActiveMaterialSlotCount() {
    return Math.min(
        getMaterialRequirements().size(), StructurePrinterBlockEntity.MATERIAL_SLOT_COUNT);
  }

  @Override
  public void slotsChanged(Container container) {
    super.slotsChanged(container);
    refreshLayout();
  }

  @Override
  public void broadcastChanges() {
    refreshLayout();
    super.broadcastChanges();
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    ItemStack empty = ItemStack.EMPTY;
    Slot slot = this.slots.get(index);
    if (!slot.hasItem()) {
      return empty;
    }

    ItemStack stack = slot.getItem();
    ItemStack original = stack.copy();
    if (index < CONTAINER_SLOT_COUNT) {
      if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
        return ItemStack.EMPTY;
      }
    } else if (stack.is(ModItems.BLUEPRINT.get())) {
      if (!moveItemStackTo(
          stack,
          StructurePrinterBlockEntity.BLUEPRINT_SLOT,
          StructurePrinterBlockEntity.BLUEPRINT_SLOT + 1,
          false)) {
        return ItemStack.EMPTY;
      }
    } else {
      int materialIndex = findMaterialSlotFor(stack);
      if (materialIndex < 0
          || !moveItemStackTo(
              stack,
              StructurePrinterBlockEntity.FIRST_MATERIAL_SLOT + materialIndex,
              StructurePrinterBlockEntity.FIRST_MATERIAL_SLOT + materialIndex + 1,
              false)) {
        if (index < PLAYER_INVENTORY_END) {
          if (!moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
            return ItemStack.EMPTY;
          }
        } else if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
          return ItemStack.EMPTY;
        }
      }
    }

    if (stack.isEmpty()) {
      slot.setByPlayer(ItemStack.EMPTY);
    } else {
      slot.setChanged();
    }

    slot.onTake(player, stack);
    return original;
  }

  private int findMaterialSlotFor(ItemStack stack) {
    List<MaterialRequirement> requirements = getMaterialRequirements();
    for (int i = 0;
        i < Math.min(requirements.size(), StructurePrinterBlockEntity.MATERIAL_SLOT_COUNT);
        i++) {
      MaterialRequirement requirement = requirements.get(i);
      if (!stack.is(requirement.item())) {
        continue;
      }
      ItemStack existing = container.getItem(StructurePrinterBlockEntity.FIRST_MATERIAL_SLOT + i);
      if (existing.isEmpty()
          || (ItemStack.isSameItemSameComponents(existing, stack)
              && existing.getCount() < existing.getMaxStackSize())) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public boolean stillValid(Player player) {
    return stillValid(access, player, ModBlocks.STRUCTURE_PRINTER.get());
  }

  @Override
  public void removed(Player player) {
    super.removed(player);
    container.stopOpen(player);
  }

  public int getProgress() {
    return progressData.get(StructurePrinterBlockEntity.DATA_PROGRESS);
  }

  public int getMaxProgress() {
    return progressData.get(StructurePrinterBlockEntity.DATA_MAX_PROGRESS);
  }

  public int getScaledProgress(int width) {
    int maxProgress = getMaxProgress();
    if (maxProgress <= 0 || getProgress() <= 0) {
      return 0;
    }
    return Math.min(width, getProgress() * width / maxProgress);
  }

  private class MaterialSlot extends Slot {
    private final int materialIndex;

    private MaterialSlot(Container container, int slot, int x, int y, int materialIndex) {
      super(container, slot, x, y);
      this.materialIndex = materialIndex;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
      MaterialRequirement requirement = getMaterialRequirement(materialIndex);
      return requirement != null && stack.is(requirement.item());
    }

    @Override
    public boolean isActive() {
      int firstVisible = scrollRow * MATERIAL_COLUMNS;
      int lastVisible = firstVisible + VISIBLE_MATERIAL_SLOTS;
      if (materialIndex < firstVisible || materialIndex >= lastVisible) {
        return false;
      }
      return isMaterialSlotPresent(materialIndex);
    }
  }
}
