package io.github.ooboomberoo.precaststructure.structure.special;

import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.jetbrains.annotations.Nullable;

/**
 * Beds are two blocks. Only the head contributes a material; both halves stay in the blueprint
 * for correct placement and clear. Preview mesh (including the foot) comes from the generic BER
 * path via {@link io.github.ooboomberoo.precaststructure.client.special.BlockEntityPreviewRenderer}.
 */
public final class BedSpecialHandler implements SpecialBlockHandler {
    @Override
    public boolean matches(BlockState state) {
        return state.getBlock() instanceof BedBlock;
    }

    @Override
    public @Nullable CompoundTag sanitizeCapturedNbt(BlockState state, @Nullable CompoundTag nbt) {
        return InventoryNbt.stripContainerContents(nbt);
    }

    @Override
    public OptionalInt materialUnits(BlockState state) {
        return state.getValue(BedBlock.PART) == BedPart.HEAD ? OptionalInt.of(1) : OptionalInt.of(0);
    }
}
