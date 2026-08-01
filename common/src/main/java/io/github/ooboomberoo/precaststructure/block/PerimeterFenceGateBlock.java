package io.github.ooboomberoo.precaststructure.block;

import io.github.ooboomberoo.precaststructure.structure.StructureFrameDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;

/** Perimeter fence gate that notifies scanners when the scan frame changes. */
public class PerimeterFenceGateBlock extends FenceGateBlock {
    public PerimeterFenceGateBlock(Properties properties, WoodType type) {
        super(properties, type);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            StructureFrameDetector.notifyScannersNear(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean changed = !state.is(newState.getBlock());
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (changed) {
            StructureFrameDetector.notifyScannersNear(level, pos);
        }
    }
}
