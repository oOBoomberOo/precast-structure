package io.github.ooboomberoo.precaststructure.structure;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class StructurePlacement {
    private StructurePlacement() {
    }

    public static BlockPos resolveOrigin(UseOnContext context) {
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = context.getLevel().getBlockState(clickedPos);
        return clickedState.canBeReplaced() ? clickedPos : clickedPos.relative(context.getClickedFace());
    }

    public static Optional<BlockPos> firstBlockedPosition(Level level, BlockPos origin, StructureBlueprint blueprint) {
        for (StructureBlockInfo block : blueprint.blocks()) {
            BlockPos targetPos = origin.offset(block.offset());
            if (!isReplaceable(level.getBlockState(targetPos))) {
                return Optional.of(targetPos);
            }
        }
        return Optional.empty();
    }

    public static boolean isReplaceable(BlockState state) {
        return state.isAir()
            || state.canBeReplaced()
            || state.is(BlockTags.LEAVES)
            || state.is(BlockTags.FLOWERS)
            || state.is(Blocks.SHORT_GRASS)
            || state.is(Blocks.TALL_GRASS)
            || state.is(Blocks.FERN)
            || state.is(Blocks.LARGE_FERN)
            || state.getFluidState().is(FluidTags.WATER);
    }

    public static void place(Level level, BlockPos origin, StructureBlueprint blueprint) {
        for (StructureBlockInfo block : blueprint.blocks()) {
            level.setBlock(origin.offset(block.offset()), block.state(), 3);
        }
    }
}
