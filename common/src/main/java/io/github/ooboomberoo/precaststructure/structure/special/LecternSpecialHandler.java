package io.github.ooboomberoo.precaststructure.structure.special;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Lectern book content must not copy into replicas. Strip Book/Page NBT and clear
 * {@link BlockStateProperties#HAS_BOOK}.
 *
 * <p>Does not override render: the lectern BER only draws the book; the stand comes from the
 * block model. Forcing BER (and returning true) made empty lecterns invisible.
 */
public final class LecternSpecialHandler implements SpecialBlockHandler {
    @Override
    public boolean matches(BlockState state) {
        return state.getBlock() instanceof LecternBlock;
    }

    @Override
    public BlockState sanitizeCapturedState(BlockState state) {
        if (state.hasProperty(BlockStateProperties.HAS_BOOK)
            && state.getValue(BlockStateProperties.HAS_BOOK)) {
            return state.setValue(BlockStateProperties.HAS_BOOK, false);
        }
        return state;
    }

    @Override
    public @Nullable CompoundTag sanitizeCapturedNbt(BlockState state, @Nullable CompoundTag nbt) {
        return InventoryNbt.stripContainerContents(nbt);
    }
}
