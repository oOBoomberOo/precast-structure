package io.github.ooboomberoo.precaststructure.menu;

import io.github.ooboomberoo.precaststructure.block.entity.StructureScannerBlockEntity;
import io.github.ooboomberoo.precaststructure.registry.ModBlocks;
import io.github.ooboomberoo.precaststructure.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

public class StructureScannerMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final BlockPos blockPos;
    private final String initialStructureName;

    public StructureScannerMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), buf.readUtf(StructureScannerBlockEntity.MAX_NAME_LENGTH));
    }

    public StructureScannerMenu(int containerId, Inventory inventory, BlockPos blockPos, String initialStructureName) {
        super(ModMenuTypes.STRUCTURE_SCANNER.get(), containerId);
        this.blockPos = blockPos;
        this.initialStructureName = initialStructureName;
        this.access = ContainerLevelAccess.create(inventory.player.level(), blockPos);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public String getInitialStructureName() {
        return initialStructureName;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.STRUCTURE_SCANNER.get());
    }
}
