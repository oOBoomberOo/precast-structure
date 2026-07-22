package io.github.ooboomberoo.precaststructure.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

class StructureBlueprintTest {
    @Test
    void roundTripsSavedStructureData() {
        StructureBlueprint blueprint = new StructureBlueprint(
            new BlockPos(2, 3, 4),
            List.of(
                new StructureBlockInfo(new BlockPos(0, 0, 0), Blocks.STONE.defaultBlockState()),
                new StructureBlockInfo(new BlockPos(1, 2, 3), Blocks.OAK_PLANKS.defaultBlockState())
            )
        );

        StructureBlueprint loaded = StructureBlueprint.load(blueprint.save()).orElseThrow();

        assertEquals(blueprint.size(), loaded.size());
        assertEquals(blueprint.blocks().size(), loaded.blocks().size());
        assertEquals(Blocks.STONE, loaded.blocks().getFirst().state().getBlock());
        assertEquals(Blocks.OAK_PLANKS, loaded.blocks().get(1).state().getBlock());
    }

    @Test
    void countsRequiredMaterials() {
        StructureBlueprint blueprint = new StructureBlueprint(
            new BlockPos(1, 1, 2),
            List.of(
                new StructureBlockInfo(BlockPos.ZERO, Blocks.STONE.defaultBlockState()),
                new StructureBlockInfo(new BlockPos(0, 0, 1), Blocks.STONE.defaultBlockState())
            )
        );

        assertEquals(2, blueprint.requiredItems().get(Blocks.STONE.asItem()));
    }

    @Test
    void replaceableFilterAllowsFoliageAndWaterButNotSolidBlocks() {
        assertTrue(StructurePlacement.isReplaceable(Blocks.AIR.defaultBlockState()));
        assertTrue(StructurePlacement.isReplaceable(Blocks.WATER.defaultBlockState()));
        assertTrue(StructurePlacement.isReplaceable(Blocks.OAK_LEAVES.defaultBlockState()));
        assertTrue(StructurePlacement.isReplaceable(Blocks.SHORT_GRASS.defaultBlockState()));
        assertTrue(!StructurePlacement.isReplaceable(Blocks.DIRT.defaultBlockState()));
    }
}
