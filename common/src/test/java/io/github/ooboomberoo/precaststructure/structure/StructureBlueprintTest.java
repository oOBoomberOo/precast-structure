package io.github.ooboomberoo.precaststructure.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

class StructureBlueprintTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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
        assertEquals(Blocks.STONE, loaded.blocks().get(0).state().getBlock());
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
    void materialSlotsAreOnePerUniqueItem() {
        StructureBlueprint blueprint = new StructureBlueprint(
            new BlockPos(2, 1, 1),
            List.of(
                new StructureBlockInfo(BlockPos.ZERO, Blocks.OAK_STAIRS.defaultBlockState()),
                new StructureBlockInfo(new BlockPos(0, 0, 0), Blocks.OAK_STAIRS.defaultBlockState()),
                new StructureBlockInfo(new BlockPos(1, 0, 0), Blocks.COBBLESTONE.defaultBlockState())
            )
        );

        var slots = blueprint.materialSlotRequirements();
        assertEquals(2, slots.size());
        assertEquals(Blocks.OAK_STAIRS.asItem(), slots.get(0).item());
        assertEquals(2, slots.get(0).amount());
        assertEquals(Blocks.COBBLESTONE.asItem(), slots.get(1).item());
        assertEquals(1, slots.get(1).amount());
    }

    @Test
    void materialSlotsSplitWhenAmountExceedsMaxStack() {
        var blocks = new java.util.ArrayList<StructureBlockInfo>();
        for (int i = 0; i < 65; i++) {
            blocks.add(new StructureBlockInfo(new BlockPos(i % 16, 0, i / 16), Blocks.COBBLESTONE.defaultBlockState()));
        }
        StructureBlueprint blueprint = new StructureBlueprint(new BlockPos(16, 1, 5), blocks);

        var slots = blueprint.materialSlotRequirements();
        assertEquals(2, slots.size());
        assertEquals(64, slots.get(0).amount());
        assertEquals(1, slots.get(1).amount());
        assertEquals(Blocks.COBBLESTONE.asItem(), slots.get(0).item());
        assertEquals(Blocks.COBBLESTONE.asItem(), slots.get(1).item());
    }

    @Test
    void replaceableFilterAllowsFoliageAndWaterButNotSolidBlocks() {
        assertTrue(StructurePlacement.isReplaceable(Blocks.AIR.defaultBlockState()));
        assertTrue(StructurePlacement.isReplaceable(Blocks.WATER.defaultBlockState()));
        assertFalse(StructurePlacement.isReplaceable(Blocks.DIRT.defaultBlockState()));
        assertFalse(StructurePlacement.isReplaceable(Blocks.STONE.defaultBlockState()));
    }

    @Test
    void frontCenterUsesMiddleOfFrontFace() {
        assertEquals(new BlockPos(1, 0, 0), StructurePlacement.frontCenterLocal(new BlockPos(3, 4, 3)));
        assertEquals(new BlockPos(0, 0, 0), StructurePlacement.frontCenterLocal(new BlockPos(1, 3, 1)));
        assertEquals(new BlockPos(1, 0, 0), StructurePlacement.frontCenterLocal(new BlockPos(4, 1, 4)));
    }

    @Test
    void contentFrontCenterIgnoresEmptyLeadingSpaceInScanVolume() {
        StructureBlueprint pillarInLargerVolume = new StructureBlueprint(
            new BlockPos(3, 3, 3),
            List.of(
                new StructureBlockInfo(new BlockPos(1, 0, 1), Blocks.OAK_LOG.defaultBlockState()),
                new StructureBlockInfo(new BlockPos(1, 1, 1), Blocks.OAK_LOG.defaultBlockState()),
                new StructureBlockInfo(new BlockPos(1, 2, 1), Blocks.OAK_LOG.defaultBlockState())
            )
        );

        assertEquals(new BlockPos(1, 0, 1), StructurePlacement.contentFrontCenter(pillarInLargerVolume));
        assertEquals(BlockPos.ZERO, StructurePlacement.transformOffset(new BlockPos(1, 0, 1), pillarInLargerVolume, Direction.SOUTH));
        assertEquals(new BlockPos(0, 1, 0), StructurePlacement.transformOffset(new BlockPos(1, 1, 1), pillarInLargerVolume, Direction.SOUTH));
    }

    @Test
    void transformOffsetAnchorsFrontCenterAndRotatesWithFacing() {
        BlockPos size = new BlockPos(3, 1, 3);
        BlockPos frontCenter = new BlockPos(1, 0, 0);
        BlockPos backCenter = new BlockPos(1, 0, 2);

        assertEquals(BlockPos.ZERO, StructurePlacement.transformOffset(frontCenter, size, Direction.SOUTH));
        assertEquals(new BlockPos(-1, 0, 0), StructurePlacement.transformOffset(new BlockPos(0, 0, 0), size, Direction.SOUTH));
        // Structure extends away along look direction (south => +Z)
        assertEquals(new BlockPos(0, 0, 2), StructurePlacement.transformOffset(backCenter, size, Direction.SOUTH));
        assertEquals(BlockPos.ZERO, StructurePlacement.transformOffset(frontCenter, size, Direction.NORTH));
        assertEquals(new BlockPos(0, 0, -2), StructurePlacement.transformOffset(backCenter, size, Direction.NORTH));
        assertEquals(new BlockPos(0, 0, -1), StructurePlacement.transformOffset(new BlockPos(0, 0, 0), size, Direction.WEST));
    }

    @Test
    void scanForwardPointsIntoStructureBehindScanner() {
        assertEquals(Direction.NORTH, StructurePlacement.scanForward(Direction.SOUTH));
        assertEquals(Direction.EAST, StructurePlacement.scanForward(Direction.WEST));
    }

    @Test
    void localToScanWorldRoundTripsAabbOffsetsForEachScannerFacing() {
        BlockPos origin = new BlockPos(10, 64, 20);
        BlockPos aabbSize = new BlockPos(3, 2, 5);
        for (Direction scannerFacing : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            Direction forward = StructurePlacement.scanForward(scannerFacing);
            Rotation toLocal = StructurePlacement.inverse(StructurePlacement.rotationFor(forward));
            BlockPos minCorner = StructurePlacement.rotatedAabbMinCorner(aabbSize, toLocal);

            for (int x = 0; x < aabbSize.getX(); x++) {
                for (int y = 0; y < aabbSize.getY(); y++) {
                    for (int z = 0; z < aabbSize.getZ(); z++) {
                        BlockPos aabbOffset = new BlockPos(x, y, z);
                        BlockPos local = StructurePlacement.rotateOffset(aabbOffset, toLocal).subtract(minCorner);
                        BlockPos world = StructurePlacement.localToScanWorld(origin, aabbSize, scannerFacing, local);
                        assertEquals(origin.offset(aabbOffset), world, "facing=" + scannerFacing + " offset=" + aabbOffset);
                    }
                }
            }
        }
    }

    @Test
    void captureOrientsFrontTowardScannerNotWorldPositiveZ() {
        // Scanner faces south (front toward +Z); structure sits to the north (forward = NORTH).
        // A stair facing the scanner (south) must become a local south-facing stair at z=0 front.
        Direction scannerFacing = Direction.SOUTH;
        Direction forward = StructurePlacement.scanForward(scannerFacing);
        assertEquals(Direction.NORTH, forward);

        Rotation toLocal = StructurePlacement.inverse(StructurePlacement.rotationFor(forward));
        BlockPos aabbSize = new BlockPos(1, 1, 3);
        BlockPos minCorner = StructurePlacement.rotatedAabbMinCorner(aabbSize, toLocal);

        // World AABB: z=0 is north (back), z=2 is south (front toward scanner).
        BlockPos frontAabb = new BlockPos(0, 0, 2);
        BlockPos backAabb = new BlockPos(0, 0, 0);
        BlockPos frontLocal = StructurePlacement.rotateOffset(frontAabb, toLocal).subtract(minCorner);
        BlockPos backLocal = StructurePlacement.rotateOffset(backAabb, toLocal).subtract(minCorner);

        assertEquals(0, frontLocal.getZ(), "front toward scanner should be local z=0");
        assertEquals(2, backLocal.getZ(), "back should be local +Z");
    }
}
