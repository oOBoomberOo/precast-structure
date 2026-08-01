package io.github.ooboomberoo.precaststructure.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RailRotationTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void poweredAndRegularRailsRotateConsistently() {
        BlockState poweredNS = Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_SOUTH);
        BlockState railNS = Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH);

        for (Direction facing : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            RailShape poweredShape = StructurePlacement.transformState(poweredNS, facing).getValue(PoweredRailBlock.SHAPE);
            RailShape railShape = StructurePlacement.transformState(railNS, facing).getValue(RailBlock.SHAPE);
            assertEquals(railShape, poweredShape, "facing=" + facing);
        }
    }

    @Test
    void clockwise180KeepsFlatPoweredRailAxis() {
        // Vanilla PoweredRailBlock.rotate(CLOCKWISE_180) wrongly maps NORTH_SOUTH -> EAST_WEST (MC-196102).
        assertEquals(
            RailShape.EAST_WEST,
            Blocks.POWERED_RAIL.defaultBlockState()
                .setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_SOUTH)
                .rotate(Rotation.CLOCKWISE_180)
                .getValue(PoweredRailBlock.SHAPE),
            "precondition: vanilla bug still present"
        );

        assertEquals(
            RailShape.NORTH_SOUTH,
            StructurePlacement.rotateState(
                Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_SOUTH),
                Rotation.CLOCKWISE_180
            ).getValue(PoweredRailBlock.SHAPE)
        );
        assertEquals(
            RailShape.EAST_WEST,
            StructurePlacement.rotateState(
                Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST),
                Rotation.CLOCKWISE_180
            ).getValue(PoweredRailBlock.SHAPE)
        );
        assertEquals(
            RailShape.ASCENDING_SOUTH,
            StructurePlacement.rotateState(
                Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_NORTH),
                Rotation.CLOCKWISE_180
            ).getValue(PoweredRailBlock.SHAPE)
        );
    }

    @Test
    void detectorAndActivatorMatchRegularRailRotation() {
        for (RailShape shape : new RailShape[] {
            RailShape.NORTH_SOUTH,
            RailShape.EAST_WEST,
            RailShape.ASCENDING_NORTH,
            RailShape.ASCENDING_EAST
        }) {
            for (Rotation rotation : Rotation.values()) {
                RailShape expected = StructurePlacement.rotateState(
                    Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape),
                    rotation
                ).getValue(RailBlock.SHAPE);

                assertEquals(
                    expected,
                    StructurePlacement.rotateState(
                        Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape),
                        rotation
                    ).getValue(PoweredRailBlock.SHAPE),
                    "powered " + shape + " " + rotation
                );
                assertEquals(
                    expected,
                    StructurePlacement.rotateState(
                        Blocks.DETECTOR_RAIL.defaultBlockState().setValue(DetectorRailBlock.SHAPE, shape),
                        rotation
                    ).getValue(DetectorRailBlock.SHAPE),
                    "detector " + shape + " " + rotation
                );
                assertEquals(
                    expected,
                    StructurePlacement.rotateState(
                        Blocks.ACTIVATOR_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, shape),
                        rotation
                    ).getValue(PoweredRailBlock.SHAPE),
                    "activator " + shape + " " + rotation
                );
            }
        }
    }
}
