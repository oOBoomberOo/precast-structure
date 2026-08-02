package io.github.ooboomberoo.precaststructure.structure.special;

import java.util.OptionalInt;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Vertical double blocks (doors, tall flowers): both halves stay for placement/render, but only
 * the lower half costs an item.
 */
public final class DoubleBlockSpecialHandler implements SpecialBlockHandler {
    @Override
    public boolean matches(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof DoublePlantBlock;
    }

    @Override
    public OptionalInt materialUnits(BlockState state) {
        if (state.hasProperty(DoorBlock.HALF)) {
            return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                ? OptionalInt.of(1)
                : OptionalInt.of(0);
        }
        if (state.hasProperty(DoublePlantBlock.HALF)) {
            return state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER
                ? OptionalInt.of(1)
                : OptionalInt.of(0);
        }
        return OptionalInt.empty();
    }
}
