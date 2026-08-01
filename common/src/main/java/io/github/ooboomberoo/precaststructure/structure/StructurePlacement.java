package io.github.ooboomberoo.precaststructure.structure;

import io.github.ooboomberoo.precaststructure.registry.ModBlockTags;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public final class StructurePlacement {
    private StructurePlacement() {
    }

    public static BlockPos resolveOrigin(UseOnContext context) {
        BlockPos clickedPos = context.getClickedPos();
        BlockState clickedState = context.getLevel().getBlockState(clickedPos);
        return clickedState.canBeReplaced() ? clickedPos : clickedPos.relative(context.getClickedFace());
    }

    /**
     * Local +Z points toward the back of the structure. The front face is at local z = 0.
     * Capture bakes scanner facing into this local frame (front toward the scanner).
     * Placement facing rotates the structure so it extends away along the player's look direction,
     * with the clicked/origin block at the center of the front face (ground row).
     */
    public static Rotation rotationFor(Direction facing) {
        return switch (facing.getAxis().isVertical() ? Direction.NORTH : facing) {
            case SOUTH -> Rotation.NONE;
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static Rotation inverse(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> rotation;
        };
    }

    /**
     * World direction the structure extends away from the scanner (into the framed volume).
     * Scanner {@code FACING} is the front of the block (toward the player when placed), so the
     * platform/structure sits behind it.
     */
    public static Direction scanForward(Direction scannerFacing) {
        Direction horizontal = scannerFacing.getAxis().isVertical() ? Direction.NORTH : scannerFacing;
        return horizontal.getOpposite();
    }

    /** Min corner of an axis-aligned size box after {@code rotation} (used to normalize capture). */
    public static BlockPos rotatedAabbMinCorner(BlockPos aabbSize, Rotation rotation) {
        int maxX = Math.max(0, aabbSize.getX() - 1);
        int maxY = Math.max(0, aabbSize.getY() - 1);
        int maxZ = Math.max(0, aabbSize.getZ() - 1);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (int x : new int[]{0, maxX}) {
            for (int y : new int[]{0, maxY}) {
                for (int z : new int[]{0, maxZ}) {
                    BlockPos rotated = rotateOffset(new BlockPos(x, y, z), rotation);
                    minX = Math.min(minX, rotated.getX());
                    minY = Math.min(minY, rotated.getY());
                    minZ = Math.min(minZ, rotated.getZ());
                }
            }
        }
        return new BlockPos(minX, minY, minZ);
    }

    public static BlockPos rotatedAabbSize(BlockPos aabbSize, Rotation rotation) {
        int maxX = Math.max(0, aabbSize.getX() - 1);
        int maxY = Math.max(0, aabbSize.getY() - 1);
        int maxZ = Math.max(0, aabbSize.getZ() - 1);
        BlockPos min = rotatedAabbMinCorner(aabbSize, rotation);
        int hiX = Integer.MIN_VALUE;
        int hiY = Integer.MIN_VALUE;
        int hiZ = Integer.MIN_VALUE;
        for (int x : new int[]{0, maxX}) {
            for (int y : new int[]{0, maxY}) {
                for (int z : new int[]{0, maxZ}) {
                    BlockPos rotated = rotateOffset(new BlockPos(x, y, z), rotation);
                    hiX = Math.max(hiX, rotated.getX());
                    hiY = Math.max(hiY, rotated.getY());
                    hiZ = Math.max(hiZ, rotated.getZ());
                }
            }
        }
        return new BlockPos(hiX - min.getX() + 1, hiY - min.getY() + 1, hiZ - min.getZ() + 1);
    }

    /**
     * Maps a scanner-local blueprint offset back into the world AABB used during scanning.
     */
    public static BlockPos localToScanWorld(BlockPos scanOrigin, BlockPos scanAabbSize, Direction scannerFacing, BlockPos localOffset) {
        Direction forward = scanForward(scannerFacing);
        Rotation toWorld = rotationFor(forward);
        Rotation toLocal = inverse(toWorld);
        BlockPos minCorner = rotatedAabbMinCorner(scanAabbSize, toLocal);
        return scanOrigin.offset(rotateOffset(localOffset.offset(minCorner), toWorld));
    }

    public static BlockState localToScanWorldState(BlockState localState, Direction scannerFacing) {
        return rotateState(localState, rotationFor(scanForward(scannerFacing)));
    }

    public static BlockPos frontCenterLocal(BlockPos size) {
        return new BlockPos((Math.max(1, size.getX()) - 1) / 2, 0, 0);
    }

    /**
     * Front-center of the solid content, not the scanned volume.
     * Empty leading rows in the blueprint size would otherwise push a pillar one block past the cursor.
     */
    public static BlockPos contentFrontCenter(StructureBlueprint blueprint) {
        List<StructureBlockInfo> blocks = blueprint.blocks();
        if (blocks.isEmpty()) {
            return frontCenterLocal(blueprint.size());
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (StructureBlockInfo block : blocks) {
            BlockPos offset = block.offset();
            minX = Math.min(minX, offset.getX());
            maxX = Math.max(maxX, offset.getX());
            minY = Math.min(minY, offset.getY());
            minZ = Math.min(minZ, offset.getZ());
        }
        return new BlockPos((minX + maxX) / 2, minY, minZ);
    }

    public static BlockPos transformOffset(BlockPos localOffset, BlockPos size, Direction facing) {
        BlockPos relative = localOffset.subtract(frontCenterLocal(size));
        return rotateOffset(relative, rotationFor(facing));
    }

    public static BlockPos transformOffset(BlockPos localOffset, StructureBlueprint blueprint, Direction facing) {
        BlockPos relative = localOffset.subtract(contentFrontCenter(blueprint));
        return rotateOffset(relative, rotationFor(facing));
    }

    public static BlockPos rotateOffset(BlockPos offset, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
            case CLOCKWISE_180 -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX());
            default -> offset;
        };
    }

    public static BlockState transformState(BlockState state, Direction facing) {
        return rotateState(state, rotationFor(facing));
    }

    /**
     * Vanilla {@code PoweredRailBlock}/{@code DetectorRailBlock#rotate} mishandles flat
     * {@code NORTH_SOUTH}/{@code EAST_WEST} under {@link Rotation#CLOCKWISE_180} (MC-196102),
     * turning north-south powered rails east-west while regular rails stay correct.
     * Delegate straight-rail rotation through {@link RailBlock}, which implements it properly.
     */
    public static BlockState rotateState(BlockState state, Rotation rotation) {
        if (rotation == Rotation.NONE) {
            return state;
        }
        if (state.getBlock() instanceof BaseRailBlock railBlock && !(state.getBlock() instanceof RailBlock)) {
            return rotateStraightRail(state, railBlock, rotation);
        }
        return state.rotate(rotation);
    }

    private static BlockState rotateStraightRail(BlockState state, BaseRailBlock railBlock, Rotation rotation) {
        Property<RailShape> shapeProperty = railBlock.getShapeProperty();
        RailShape shape = state.getValue(shapeProperty);
        RailShape rotated = Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape).rotate(rotation).getValue(RailBlock.SHAPE);
        if (!shapeProperty.getPossibleValues().contains(rotated)) {
            return state;
        }
        return state.setValue(shapeProperty, rotated);
    }

    public static Optional<BlockPos> firstBlockedPosition(Level level, BlockPos origin, StructureBlueprint blueprint, Direction facing) {
        for (StructureBlockInfo block : blueprint.blocks()) {
            BlockPos targetPos = origin.offset(transformOffset(block.offset(), blueprint, facing));
            if (!isReplaceable(level.getBlockState(targetPos))) {
                return Optional.of(targetPos);
            }
        }
        return Optional.empty();
    }

    /** Axis-aligned volume occupied by a placement of {@code blueprint} at {@code origin}. */
    public static AABB placementBounds(BlockPos origin, StructureBlueprint blueprint, Direction facing) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (StructureBlockInfo block : blueprint.blocks()) {
            BlockPos targetPos = origin.offset(transformOffset(block.offset(), blueprint, facing));
            minX = Math.min(minX, targetPos.getX());
            minY = Math.min(minY, targetPos.getY());
            minZ = Math.min(minZ, targetPos.getZ());
            maxX = Math.max(maxX, targetPos.getX());
            maxY = Math.max(maxY, targetPos.getY());
            maxZ = Math.max(maxZ, targetPos.getZ());
        }
        if (blueprint.blocks().isEmpty()) {
            return new AABB(origin);
        }
        return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    public static boolean isReplaceable(BlockState state) {
        return state.isAir()
            || state.canBeReplaced()
            || state.is(BlockTags.LEAVES)
            || state.is(BlockTags.FLOWERS)
            || state.is(ModBlockTags.STRUCTURE_REPLACEABLE)
            || state.getFluidState().is(FluidTags.WATER);
    }

    public static void place(Level level, BlockPos origin, StructureBlueprint blueprint, Direction facing) {
        Set<SoundEvent> playedSounds = new HashSet<>();
        for (StructureBlockInfo block : blueprint.blocks()) {
            placeOne(level, origin, blueprint, facing, block, playedSounds);
        }
    }

    /**
     * Places a single blueprint block. Returns {@code true} if a block was written.
     * When {@code playedSounds} is non-null, each distinct place sound plays at most once.
     */
    public static boolean placeOne(
        Level level,
        BlockPos origin,
        StructureBlueprint blueprint,
        Direction facing,
        StructureBlockInfo block,
        @Nullable Set<SoundEvent> playedSounds
    ) {
        BlockPos targetPos = origin.offset(transformOffset(block.offset(), blueprint, facing));
        if (!isReplaceable(level.getBlockState(targetPos))) {
            return false;
        }
        BlockState state = transformState(block.state(), facing);
        level.setBlock(targetPos, state, 3);
        SoundType soundType = state.getSoundType();
        if (playedSounds == null) {
            playPlaceSound(level, targetPos, soundType);
        } else if (playedSounds.add(soundType.getPlaceSound())) {
            playPlaceSound(level, targetPos, soundType);
        }
        return true;
    }

    public static boolean placeOne(Level level, BlockPos origin, StructureBlueprint blueprint, Direction facing, StructureBlockInfo block) {
        return placeOne(level, origin, blueprint, facing, block, null);
    }

    private static void playPlaceSound(Level level, BlockPos pos, SoundType soundType) {
        level.playSound(
            null,
            pos,
            soundType.getPlaceSound(),
            SoundSource.BLOCKS,
            (soundType.getVolume() + 1.0F) / 2.0F,
            soundType.getPitch() * 0.8F
        );
    }
}
