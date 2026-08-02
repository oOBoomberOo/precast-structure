package io.github.ooboomberoo.precaststructure.structure;

import io.github.ooboomberoo.precaststructure.block.entity.StructureScannerBlockEntity;
import io.github.ooboomberoo.precaststructure.config.ModConfig;
import io.github.ooboomberoo.precaststructure.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class StructureFrameDetector {
    private static final Direction[] HORIZONTAL = {
        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private StructureFrameDetector() {
    }

    /**
     * Recheck any scanners that could be attached to a platform connected to {@code origin}.
     * Neighbor updates alone only reach the scanner when an adjacent block changes, so frame
     * parts call this on place/break.
     */
    public static void notifyScannersNear(Level level, BlockPos origin) {
        if (level.isClientSide()) {
            return;
        }

        Set<BlockPos> scanners = new HashSet<>();
        collectLocalScanners(level, origin, scanners);

        Set<BlockPos> platformSeeds = new HashSet<>();
        collectPlatformSeeds(level, origin, platformSeeds);
        for (BlockPos seed : platformSeeds) {
            Set<BlockPos> platform = floodFillPlatform(level, List.of(seed));
            for (BlockPos floor : platform) {
                for (Direction direction : HORIZONTAL) {
                    BlockPos neighbor = floor.relative(direction);
                    if (level.getBlockEntity(neighbor) instanceof StructureScannerBlockEntity) {
                        scanners.add(neighbor.immutable());
                    }
                }
            }
        }

        for (BlockPos scannerPos : scanners) {
            BlockEntity blockEntity = level.getBlockEntity(scannerPos);
            if (blockEntity instanceof StructureScannerBlockEntity scanner) {
                scanner.recheckReady();
            }
        }
    }

    private static void collectLocalScanners(Level level, BlockPos origin, Set<BlockPos> scanners) {
        for (int dy = -1; dy <= 1; dy++) {
            for (Direction direction : HORIZONTAL) {
                BlockPos neighbor = origin.relative(direction).offset(0, dy, 0);
                if (level.getBlockEntity(neighbor) instanceof StructureScannerBlockEntity) {
                    scanners.add(neighbor.immutable());
                }
            }
            BlockPos vertical = origin.offset(0, dy, 0);
            if (level.getBlockEntity(vertical) instanceof StructureScannerBlockEntity) {
                scanners.add(vertical.immutable());
            }
        }
    }

    private static void collectPlatformSeeds(Level level, BlockPos origin, Set<BlockPos> seeds) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 0; dy++) {
            cursor.set(origin.getX(), origin.getY() + dy, origin.getZ());
            if (isPlatform(level.getBlockState(cursor))) {
                seeds.add(cursor.immutable());
            }
            for (Direction direction : HORIZONTAL) {
                cursor.setWithOffset(origin, direction);
                cursor.setY(origin.getY() + dy);
                if (isPlatform(level.getBlockState(cursor))) {
                    seeds.add(cursor.immutable());
                }
            }
        }
    }

    public static ScanResult detect(Level level, BlockPos scannerPos) {
        List<BlockPos> seeds = findAdjacentPlatformSeeds(level, scannerPos);
        if (seeds.isEmpty()) {
            return ScanResult.error(Component.translatable("message.precast_structure.invalid_platform"));
        }

        Set<BlockPos> platform = floodFillPlatform(level, seeds);
        PlatformBounds bounds = PlatformBounds.of(platform);
        ModConfig.Frame frameLimits = ModConfig.get().frame;
        if (bounds == null
                || bounds.width() < frameLimits.minPlatformSize
                || bounds.depth() < frameLimits.minPlatformSize
                || bounds.width() > frameLimits.maxPlatformSize
                || bounds.depth() > frameLimits.maxPlatformSize) {
            return ScanResult.error(Component.translatable("message.precast_structure.invalid_platform"));
        }

        if (!isFilledRectangle(platform, bounds)) {
            return ScanResult.error(Component.translatable("message.precast_structure.invalid_floor"));
        }

        // Classic placement is beside the platform at floor Y; fence-line placement sits on the
        // border one block above. Reject interior/off-frame scanners so they are not digitized.
        if (!isScannerAttachedToPlatform(scannerPos, bounds)) {
            return ScanResult.error(Component.translatable("message.precast_structure.invalid_platform"));
        }

        CornerScaffold scaffold = findCornerScaffold(level, bounds);
        if (scaffold == null) {
            return ScanResult.error(Component.translatable("message.precast_structure.invalid_height"));
        }

        if (!hasFenceRing(level, bounds, scaffold.corner())) {
            return ScanResult.error(Component.translatable("message.precast_structure.invalid_fence"));
        }

        BlockPos interiorOrigin = new BlockPos(bounds.minX() + 1, bounds.y() + 1, bounds.minZ() + 1);
        BlockPos size = new BlockPos(bounds.width() - 2, scaffold.height(), bounds.depth() - 2);
        return ScanResult.success(new StructureFrame(interiorOrigin, size));
    }

    private static List<BlockPos> findAdjacentPlatformSeeds(Level level, BlockPos scannerPos) {
        List<BlockPos> seeds = new ArrayList<>(4);
        for (Direction direction : HORIZONTAL) {
            BlockPos neighbor = scannerPos.relative(direction);
            if (isPlatform(level.getBlockState(neighbor))) {
                seeds.add(neighbor.immutable());
            }
        }
        if (!seeds.isEmpty()) {
            return seeds;
        }
        // Fence-line / scaffold placement: scanner sits on a border platform floor.
        BlockPos below = scannerPos.below();
        if (isPlatform(level.getBlockState(below))) {
            seeds.add(below.immutable());
        }
        return seeds;
    }

    static boolean isScannerAttachedToPlatform(BlockPos scannerPos, PlatformBounds bounds) {
        if (scannerPos.getY() == bounds.y()) {
            return isHorizontallyAdjacentToBounds(scannerPos, bounds);
        }
        if (scannerPos.getY() == bounds.y() + 1) {
            return isOnPlatformBorder(scannerPos, bounds);
        }
        return false;
    }

    private static boolean isHorizontallyAdjacentToBounds(BlockPos scannerPos, PlatformBounds bounds) {
        int x = scannerPos.getX();
        int z = scannerPos.getZ();
        boolean touchesX = (x == bounds.minX() - 1 || x == bounds.maxX() + 1)
            && z >= bounds.minZ()
            && z <= bounds.maxZ();
        boolean touchesZ = (z == bounds.minZ() - 1 || z == bounds.maxZ() + 1)
            && x >= bounds.minX()
            && x <= bounds.maxX();
        return touchesX || touchesZ;
    }

    private static boolean isOnPlatformBorder(BlockPos scannerPos, PlatformBounds bounds) {
        int x = scannerPos.getX();
        int z = scannerPos.getZ();
        if (x < bounds.minX() || x > bounds.maxX() || z < bounds.minZ() || z > bounds.maxZ()) {
            return false;
        }
        return x == bounds.minX() || x == bounds.maxX() || z == bounds.minZ() || z == bounds.maxZ();
    }

    private static Set<BlockPos> floodFillPlatform(Level level, List<BlockPos> seeds) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(seeds);
        int floorY = seeds.getFirst().getY();

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos.immutable())) {
                continue;
            }
            if (visited.size() > ModConfig.get().frame.maxPlatformSize * ModConfig.get().frame.maxPlatformSize) {
                break;
            }
            for (Direction direction : HORIZONTAL) {
                BlockPos next = pos.relative(direction);
                if (next.getY() != floorY || visited.contains(next)) {
                    continue;
                }
                if (isPlatform(level.getBlockState(next))) {
                    queue.addLast(next.immutable());
                }
            }
        }
        return visited;
    }

    static boolean isFilledRectangle(Set<BlockPos> platform, PlatformBounds bounds) {
        if (platform.size() != bounds.width() * bounds.depth()) {
            return false;
        }
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                if (!platform.contains(new BlockPos(x, bounds.y(), z))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static CornerScaffold findCornerScaffold(Level level, PlatformBounds bounds) {
        CornerScaffold best = null;
        for (BlockPos corner : bounds.corners()) {
            int height = countPillar(level, corner);
            if (height < 1) {
                continue;
            }
            if (best != null) {
                // Exactly one scaffold corner is required.
                return null;
            }
            best = new CornerScaffold(corner, height);
        }
        return best;
    }

    private static boolean hasFenceRing(Level level, PlatformBounds bounds, BlockPos scaffoldCorner) {
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                boolean onBorder = x == bounds.minX() || x == bounds.maxX() || z == bounds.minZ() || z == bounds.maxZ();
                if (!onBorder) {
                    continue;
                }
                BlockPos pos = new BlockPos(x, bounds.y(), z);
                if (pos.equals(scaffoldCorner)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos.above());
                if (!isPerimeterBarrier(state)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int countPillar(Level level, BlockPos pillarBase) {
        int maxHeight = ModConfig.get().frame.maxPlatformSize;
        int height = 0;
        while (height < maxHeight && isCornerScaffold(level.getBlockState(pillarBase.above(height + 1)))) {
            height++;
        }
        return height;
    }

    private static boolean isPlatform(BlockState state) {
        return state.is(ModBlocks.PLATFORM_FLOOR.get());
    }

    static boolean isPerimeterBarrier(BlockState state) {
        return state.is(ModBlocks.PERIMETER_FENCE.get())
            || state.is(ModBlocks.PERIMETER_FENCE_GATE.get())
            || state.is(ModBlocks.STRUCTURE_SCANNER.get());
    }

    static boolean isCornerScaffold(BlockState state) {
        return state.is(ModBlocks.METAL_SCAFFOLD.get()) || state.is(ModBlocks.STRUCTURE_SCANNER.get());
    }

    record PlatformBounds(int minX, int maxX, int minZ, int maxZ, int y) {
        static PlatformBounds of(Set<BlockPos> platform) {
            if (platform.isEmpty()) {
                return null;
            }
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            int y = platform.iterator().next().getY();
            for (BlockPos pos : platform) {
                if (pos.getY() != y) {
                    return null;
                }
                minX = Math.min(minX, pos.getX());
                maxX = Math.max(maxX, pos.getX());
                minZ = Math.min(minZ, pos.getZ());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return new PlatformBounds(minX, maxX, minZ, maxZ, y);
        }

        int width() {
            return maxX - minX + 1;
        }

        int depth() {
            return maxZ - minZ + 1;
        }

        List<BlockPos> corners() {
            return List.of(
                new BlockPos(minX, y, minZ),
                new BlockPos(maxX, y, minZ),
                new BlockPos(minX, y, maxZ),
                new BlockPos(maxX, y, maxZ)
            );
        }
    }

    private record CornerScaffold(BlockPos corner, int height) {
    }

    public record ScanResult(StructureFrame frame, Component error) {
        public static ScanResult success(StructureFrame frame) {
            return new ScanResult(frame, null);
        }

        public static ScanResult error(Component error) {
            return new ScanResult(null, error);
        }

        public boolean successful() {
            return frame != null;
        }

        public Optional<StructureFrame> frameOptional() {
            return Optional.ofNullable(frame);
        }
    }
}
