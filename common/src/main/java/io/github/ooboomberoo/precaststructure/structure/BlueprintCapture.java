package io.github.ooboomberoo.precaststructure.structure;

import io.github.ooboomberoo.precaststructure.registry.ModBlockTags;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class BlueprintCapture {
    private BlueprintCapture() {
    }

    public static StructureBlueprint capture(Level level, StructureFrame frame) {
        List<StructureBlockInfo> blocks = new ArrayList<>();
        BlockPos origin = frame.interiorOrigin();
        BlockPos size = frame.size();

        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos worldPos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(worldPos);
                    if (ModBlockTags.isBlueprintExcluded(state)) {
                        continue;
                    }
                    blocks.add(new StructureBlockInfo(new BlockPos(x, y, z), state));
                }
            }
        }

        return new StructureBlueprint(size, List.copyOf(blocks));
    }
}
