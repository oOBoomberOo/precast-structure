package io.github.ooboomberoo.precaststructure.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ooboomberoo.precaststructure.structure.StructureFrameDetector.PlatformBounds;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;

class StructureFrameDetectorTest {
    @Test
    void filledRectangleAcceptsSolidPlatform() {
        Set<BlockPos> platform = rectangle(0, 0, 0, 4, 4);
        PlatformBounds bounds = PlatformBounds.of(platform);

        assertNotNull(bounds);
        assertEquals(5, bounds.width());
        assertEquals(5, bounds.depth());
        assertTrue(StructureFrameDetector.isFilledRectangle(platform, bounds));
    }

    @Test
    void filledRectangleRejectsHole() {
        Set<BlockPos> platform = rectangle(0, 0, 0, 3, 3);
        platform.remove(new BlockPos(1, 0, 1));
        PlatformBounds bounds = PlatformBounds.of(platform);

        assertNotNull(bounds);
        assertFalse(StructureFrameDetector.isFilledRectangle(platform, bounds));
    }

    @Test
    void filledRectangleRejectsIrregularShape() {
        Set<BlockPos> platform = rectangle(0, 0, 0, 2, 2);
        platform.add(new BlockPos(3, 0, 0));
        PlatformBounds bounds = PlatformBounds.of(platform);

        assertNotNull(bounds);
        assertFalse(StructureFrameDetector.isFilledRectangle(platform, bounds));
    }

    @Test
    void platformBoundsNullForEmptySet() {
        assertNull(PlatformBounds.of(Set.of()));
    }

    @Test
    void platformBoundsExposesCorners() {
        PlatformBounds bounds = PlatformBounds.of(rectangle(10, 5, 20, 12, 22));

        assertNotNull(bounds);
        assertEquals(
            Set.of(
                new BlockPos(10, 5, 20),
                new BlockPos(12, 5, 20),
                new BlockPos(10, 5, 22),
                new BlockPos(12, 5, 22)
            ),
            Set.copyOf(bounds.corners())
        );
    }

    private static Set<BlockPos> rectangle(int minX, int y, int minZ, int maxX, int maxZ) {
        Set<BlockPos> cells = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                cells.add(new BlockPos(x, y, z));
            }
        }
        return cells;
    }
}
