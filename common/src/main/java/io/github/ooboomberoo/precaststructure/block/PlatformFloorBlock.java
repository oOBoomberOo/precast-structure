package io.github.ooboomberoo.precaststructure.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Platform floor with horizontal connected textures (borders only on open edges). */
public class PlatformFloorBlock extends StructureFrameBlock {
    public static final MapCodec<PlatformFloorBlock> CODEC = simpleCodec(PlatformFloorBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    public PlatformFloorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
            this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
        );
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connectionState(context.getLevel(), context.getClickedPos());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(this)) {
            refreshConnectionsNear(level, pos);
        }
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        Direction direction,
        BlockState neighborState,
        LevelAccessor level,
        BlockPos pos,
        BlockPos neighborPos
    ) {
        if (direction.getAxis().isHorizontal()) {
            return state.setValue(property(direction), neighborState.is(this));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    private void refreshConnectionsNear(Level level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, -1), origin.offset(1, 0, 1))) {
            if (pos.getY() != origin.getY()) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!state.is(this)) {
                continue;
            }
            BlockState connected = connectionState(level, pos);
            if (!connected.equals(state)) {
                level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
            }
        }
    }

    private BlockState connectionState(LevelAccessor level, BlockPos pos) {
        return defaultBlockState()
            .setValue(NORTH, level.getBlockState(pos.north()).is(this))
            .setValue(EAST, level.getBlockState(pos.east()).is(this))
            .setValue(SOUTH, level.getBlockState(pos.south()).is(this))
            .setValue(WEST, level.getBlockState(pos.west()).is(this));
    }

    private static BooleanProperty property(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> throw new IllegalArgumentException("Not horizontal: " + direction);
        };
    }
}
