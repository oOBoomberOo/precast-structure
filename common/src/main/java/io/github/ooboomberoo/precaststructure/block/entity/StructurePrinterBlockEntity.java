package io.github.ooboomberoo.precaststructure.block.entity;

import dev.architectury.registry.menu.ExtendedMenuProvider;
import io.github.ooboomberoo.precaststructure.menu.StructurePrinterMenu;
import io.github.ooboomberoo.precaststructure.registry.ModBlockEntityTypes;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import io.github.ooboomberoo.precaststructure.structure.BlueprintItemData;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class StructurePrinterBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, ExtendedMenuProvider {
    public static final int BLUEPRINT_SLOT = 0;
    public static final int FIRST_MATERIAL_SLOT = 1;
    public static final int MATERIAL_SLOT_COUNT = 8;
    public static final int OUTPUT_SLOT = FIRST_MATERIAL_SLOT + MATERIAL_SLOT_COUNT;
    public static final int SLOT_COUNT = OUTPUT_SLOT + 1;
    private static final int[] INPUT_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] OUTPUT_SLOTS = {OUTPUT_SLOT};

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    public StructurePrinterBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntityTypes.STRUCTURE_PRINTER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StructurePrinterBlockEntity blockEntity) {
        if (level.isClientSide()) {
            return;
        }
        blockEntity.tryPrint();
    }

    private void tryPrint() {
        ItemStack blueprintStack = items.get(BLUEPRINT_SLOT);
        if (!blueprintStack.is(ModItems.BLUEPRINT.get())) {
            return;
        }

        Optional<StructureBlueprint> optional = BlueprintItemData.read(blueprintStack, level.registryAccess());
        if (optional.isEmpty()) {
            return;
        }

        StructureBlueprint blueprint = optional.get();
        if (!hasMaterials(blueprint.requiredItems())) {
            return;
        }

        ItemStack outputStack = items.get(OUTPUT_SLOT);
        if (!outputStack.isEmpty()) {
            return;
        }

        consumeMaterials(blueprint.requiredItems());
        ItemStack structureStack = new ItemStack(ModItems.PRECAST_STRUCTURE.get());
        BlueprintItemData.write(structureStack, blueprint, blueprintStack.get(DataComponents.CUSTOM_NAME));
        items.set(OUTPUT_SLOT, structureStack);
        setChanged();
    }

    private boolean hasMaterials(Map<Item, Integer> requiredItems) {
        for (Map.Entry<Item, Integer> entry : requiredItems.entrySet()) {
            int remaining = entry.getValue();
            for (int slot = FIRST_MATERIAL_SLOT; slot < OUTPUT_SLOT && remaining > 0; slot++) {
                ItemStack stack = items.get(slot);
                if (stack.is(entry.getKey())) {
                    remaining -= stack.getCount();
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private void consumeMaterials(Map<Item, Integer> requiredItems) {
        for (Map.Entry<Item, Integer> entry : requiredItems.entrySet()) {
            int remaining = entry.getValue();
            for (int slot = FIRST_MATERIAL_SLOT; slot < OUTPUT_SLOT && remaining > 0; slot++) {
                ItemStack stack = items.get(slot);
                if (!stack.is(entry.getKey())) {
                    continue;
                }
                int consumed = Math.min(remaining, stack.getCount());
                stack.shrink(consumed);
                remaining -= consumed;
                if (stack.isEmpty()) {
                    items.set(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return direction == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) {
        if (slot == BLUEPRINT_SLOT) {
            return stack.is(ModItems.BLUEPRINT.get());
        }
        return slot >= FIRST_MATERIAL_SLOT && slot < OUTPUT_SLOT && !stack.is(ModItems.BLUEPRINT.get()) && !stack.is(ModItems.PRECAST_STRUCTURE.get());
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return slot == OUTPUT_SLOT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return canPlaceItemThroughFace(slot, stack, Direction.UP);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.precaststructure.structure_printer");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new StructurePrinterMenu(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public void saveExtraData(FriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}
