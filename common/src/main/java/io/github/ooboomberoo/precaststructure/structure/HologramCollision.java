package io.github.ooboomberoo.precaststructure.structure;

import io.github.ooboomberoo.precaststructure.config.ModConfig;
import io.github.ooboomberoo.precaststructure.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Places/removes invisible collision cubes under scan and deploy holograms.
 */
public final class HologramCollision {
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;
    private static final int CLEAR_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

    private HologramCollision() {
    }

    public static boolean enabled() {
        return ModConfig.get().hologram.solidCollision;
    }

    public static boolean isCollider(BlockState state) {
        return state.is(ModBlocks.HOLOGRAM_COLLIDER.get());
    }

    /** Replacement used when digitizing a scanned block (collider or air). */
    public static BlockState digitizedReplacement() {
        return enabled() ? ModBlocks.HOLOGRAM_COLLIDER.get().defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    public static void placeForBlueprint(Level level, BlockPos origin, StructureBlueprint blueprint, Direction facing) {
        if (!enabled() || level.isClientSide()) {
            return;
        }
        BlockState collider = ModBlocks.HOLOGRAM_COLLIDER.get().defaultBlockState();
        for (StructureBlockInfo block : blueprint.blocks()) {
            BlockPos worldPos = origin.offset(StructurePlacement.transformOffset(block.offset(), blueprint, facing));
            BlockState existing = level.getBlockState(worldPos);
            if (StructurePlacement.isReplaceable(existing) || isCollider(existing)) {
                level.setBlock(worldPos, collider, PLACE_FLAGS);
            }
        }
    }

    public static void clearFrame(Level level, StructureFrame frame) {
        if (level.isClientSide()) {
            return;
        }
        BlockPos origin = frame.interiorOrigin();
        BlockPos size = frame.size();
        for (int y = 0; y < size.getY(); y++) {
            for (int x = 0; x < size.getX(); x++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (isCollider(level.getBlockState(pos))) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), CLEAR_FLAGS);
                    }
                }
            }
        }
    }

    public static void clearBlueprint(Level level, BlockPos origin, StructureBlueprint blueprint, Direction facing) {
        if (level.isClientSide()) {
            return;
        }
        for (StructureBlockInfo block : blueprint.blocks()) {
            BlockPos worldPos = origin.offset(StructurePlacement.transformOffset(block.offset(), blueprint, facing));
            if (isCollider(level.getBlockState(worldPos))) {
                level.setBlock(worldPos, Blocks.AIR.defaultBlockState(), CLEAR_FLAGS);
            }
        }
    }
}
