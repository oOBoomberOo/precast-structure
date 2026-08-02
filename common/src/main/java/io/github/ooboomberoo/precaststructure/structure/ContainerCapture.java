package io.github.ooboomberoo.precaststructure.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Empties container-like block entities in-world before blueprint NBT is serialized, so captured
 * data naturally has no stored items. Drops contents at the block position via vanilla helpers.
 *
 * <p>Server-only: no-ops on the client. Skips ender chests (player-bound inventory).
 */
public final class ContainerCapture {
    private ContainerCapture() {
    }

    /**
     * Removes and drops inventory / single-item contents from {@code blockEntity}, updating block
     * state when required (lectern {@code has_book}, jukebox {@code has_record}, …).
     */
    public static void emptyAndDrop(Level level, BlockPos pos, BlockEntity blockEntity) {
        if (level.isClientSide()) {
            return;
        }
        BlockState state = blockEntity.getBlockState();
        if (state.getBlock() instanceof EnderChestBlock) {
            return;
        }

        if (blockEntity instanceof LecternBlockEntity lectern) {
            emptyLectern(level, pos, lectern);
            return;
        }

        if (blockEntity instanceof JukeboxBlockEntity jukebox) {
            jukebox.popOutTheItem();
            return;
        }

        if (blockEntity instanceof Container container) {
            if (blockEntity instanceof RandomizableContainer randomizable) {
                randomizable.unpackLootTable(null);
            }
            Containers.dropContents(level, pos, container);
            container.clearContent();
            blockEntity.setChanged();
        }
    }

    private static void emptyLectern(Level level, BlockPos pos, LecternBlockEntity lectern) {
        ItemStack book = lectern.getBook();
        if (book.isEmpty()) {
            return;
        }
        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), book.copy());
        lectern.clearContent();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof LecternBlock) {
            LecternBlock.resetBookState(null, level, pos, state, false);
        }
    }
}
