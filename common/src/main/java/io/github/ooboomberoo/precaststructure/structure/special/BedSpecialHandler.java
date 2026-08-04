package io.github.ooboomberoo.precaststructure.structure.special;

import java.util.OptionalInt;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Bed multi-block: both head and foot stay in the blueprint for placement and clear, but only the
 * head contributes a material unit.
 */
public final class BedSpecialHandler implements SpecialBlockHandler {
  @Override
  public boolean matches(BlockState state) {
    return state.getBlock() instanceof BedBlock;
  }

  @Override
  public OptionalInt materialUnits(BlockState state) {
    return state.getValue(BedBlock.PART) == BedPart.HEAD ? OptionalInt.of(1) : OptionalInt.of(0);
  }
}
