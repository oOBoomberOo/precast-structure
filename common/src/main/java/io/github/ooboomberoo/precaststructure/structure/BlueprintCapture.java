package io.github.ooboomberoo.precaststructure.structure;

import io.github.ooboomberoo.precaststructure.compat.CreateCompat;
import io.github.ooboomberoo.precaststructure.registry.ModBlockTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BlueprintCapture {
    private BlueprintCapture() {
    }

    /**
     * Captures the framed volume in scanner-local space: local +Z extends away from the scanner
     * into the structure, and the front face (toward the scanner) sits at local z = 0.
     * Placement then rotates that local frame to the player's look direction.
     */
    public static StructureBlueprint capture(Level level, StructureFrame frame, Direction scannerFacing) {
        List<StructureBlockInfo> blocks = new ArrayList<>();
        BlockPos origin = frame.interiorOrigin();
        BlockPos aabbSize = frame.size();

        Direction forward = StructurePlacement.scanForward(scannerFacing);
        Rotation toLocal = StructurePlacement.inverse(StructurePlacement.rotationFor(forward));
        BlockPos minCorner = StructurePlacement.rotatedAabbMinCorner(aabbSize, toLocal);
        BlockPos orientedSize = StructurePlacement.rotatedAabbSize(aabbSize, toLocal);

        for (int x = 0; x < aabbSize.getX(); x++) {
            for (int y = 0; y < aabbSize.getY(); y++) {
                for (int z = 0; z < aabbSize.getZ(); z++) {
                    BlockPos worldPos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(worldPos);
                    if (ModBlockTags.isBlueprintExcluded(state)) {
                        continue;
                    }
                    BlockPos localRaw = StructurePlacement.rotateOffset(new BlockPos(x, y, z), toLocal);
                    BlockPos localOffset = localRaw.subtract(minCorner);
                    BlockState localState = StructurePlacement.rotateState(state, toLocal);
                    CompoundTag nbt = null;
                    BlockEntity blockEntity = level.getBlockEntity(worldPos);
                    if (blockEntity != null) {
                        nbt = CreateCompat.transformNbt(
                            CreateCompat.captureBlockEntityNbt(level, blockEntity),
                            toLocal,
                            level.registryAccess()
                        );
                    }
                    blocks.add(new StructureBlockInfo(localOffset, localState, nbt));
                }
            }
        }

        return new StructureBlueprint(orientedSize, List.copyOf(blocks));
    }
}
