package io.github.ooboomberoo.precaststructure.menu;

import io.github.ooboomberoo.precaststructure.block.entity.StructurePrinterBlockEntity;
import io.github.ooboomberoo.precaststructure.registry.ModBlocks;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import io.github.ooboomberoo.precaststructure.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class StructurePrinterMenu extends AbstractContainerMenu {
    private static final int BLUEPRINT_SLOT_X = 26;
    private static final int INPUT_SLOT_X = 62;
    private static final int INPUT_SLOT_Y = 22;
    private static final int OUTPUT_SLOT_X = 134;
    private static final int OUTPUT_SLOT_Y = 31;
    private static final int SLOT_SPACING = 18;
    private static final int CONTAINER_SLOT_COUNT = StructurePrinterBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INVENTORY_START = CONTAINER_SLOT_COUNT;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_START = PLAYER_INVENTORY_END;
    private static final int HOTBAR_END = HOTBAR_START + 9;

    private final Container container;
    private final ContainerLevelAccess access;

    public StructurePrinterMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, new SimpleContainer(CONTAINER_SLOT_COUNT), ContainerLevelAccess.create(inventory.player.level(), buf.readBlockPos()));
    }

    public StructurePrinterMenu(int containerId, Inventory inventory, StructurePrinterBlockEntity blockEntity) {
        this(containerId, inventory, blockEntity, ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()));
    }

    private StructurePrinterMenu(int containerId, Inventory inventory, Container container, ContainerLevelAccess access) {
        super(ModMenuTypes.STRUCTURE_PRINTER.get(), containerId);
        checkContainerSize(container, CONTAINER_SLOT_COUNT);
        this.container = container;
        this.access = access;
        container.startOpen(inventory.player);

        this.addSlot(new Slot(container, StructurePrinterBlockEntity.BLUEPRINT_SLOT, BLUEPRINT_SLOT_X, INPUT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.BLUEPRINT.get());
            }
        });

        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 4; column++) {
                int slot = StructurePrinterBlockEntity.FIRST_MATERIAL_SLOT + row * 4 + column;
                this.addSlot(new Slot(container, slot, INPUT_SLOT_X + column * SLOT_SPACING, INPUT_SLOT_Y + row * SLOT_SPACING));
            }
        }

        this.addSlot(new Slot(container, StructurePrinterBlockEntity.OUTPUT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
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
            if (!moveItemStackTo(stack, StructurePrinterBlockEntity.BLUEPRINT_SLOT, StructurePrinterBlockEntity.BLUEPRINT_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, StructurePrinterBlockEntity.FIRST_MATERIAL_SLOT, StructurePrinterBlockEntity.OUTPUT_SLOT, false)) {
            if (index < PLAYER_INVENTORY_END) {
                if (!moveItemStackTo(stack, HOTBAR_START, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
                return ItemStack.EMPTY;
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

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.STRUCTURE_PRINTER.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
