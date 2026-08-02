package io.github.ooboomberoo.precaststructure.structure.special;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Entity-model containers (chest / ender chest / shulker). Always strip inventory / loot from
 * blueprint NBT. Preview mesh comes from the generic BER path
 * ({@link net.minecraft.world.level.block.RenderShape#ENTITYBLOCK_ANIMATED}).
 */
public final class ChestSpecialHandler implements SpecialBlockHandler {
    @Override
    public boolean matches(BlockState state) {
        return state.getBlock() instanceof ChestBlock
            || state.getBlock() instanceof EnderChestBlock
            || state.getBlock() instanceof ShulkerBoxBlock;
    }

    @Override
    public @Nullable CompoundTag sanitizeCapturedNbt(BlockState state, @Nullable CompoundTag nbt) {
        return InventoryNbt.stripContainerContents(nbt);
    }
}
