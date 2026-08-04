package io.github.ooboomberoo.precaststructure.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ooboomberoo.precaststructure.structure.StructureFrameDetector.PlatformBounds;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

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
            new BlockPos(12, 5, 22)),
        Set.copyOf(bounds.corners()));
  }

  @Test
  void scannerAttachedBesidePlatformAtFloorLevel() {
    PlatformBounds bounds = PlatformBounds.of(rectangle(0, 4, 0, 4, 4));
    assertNotNull(bounds);

    assertTrue(StructureFrameDetector.isScannerAttachedToPlatform(new BlockPos(-1, 4, 2), bounds));
    assertTrue(StructureFrameDetector.isScannerAttachedToPlatform(new BlockPos(2, 4, 5), bounds));
    assertFalse(
        StructureFrameDetector.isScannerAttachedToPlatform(new BlockPos(-1, 4, -1), bounds));
    assertFalse(StructureFrameDetector.isScannerAttachedToPlatform(new BlockPos(2, 4, 2), bounds));
  }

  @Test
  void scannerAttachedOnFenceLineBorder() {
    PlatformBounds bounds = PlatformBounds.of(rectangle(0, 4, 0, 4, 4));
    assertNotNull(bounds);

    assertTrue(StructureFrameDetector.isScannerAttachedToPlatform(new BlockPos(0, 5, 2), bounds));
    assertTrue(StructureFrameDetector.isScannerAttachedToPlatform(new BlockPos(4, 5, 4), bounds));
    assertFalse(StructureFrameDetector.isScannerAttachedToPlatform(new BlockPos(2, 5, 2), bounds));
    assertFalse(StructureFrameDetector.isScannerAttachedToPlatform(new BlockPos(0, 6, 2), bounds));
    assertFalse(StructureFrameDetector.isScannerAttachedToPlatform(new BlockPos(-1, 5, 2), bounds));
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
