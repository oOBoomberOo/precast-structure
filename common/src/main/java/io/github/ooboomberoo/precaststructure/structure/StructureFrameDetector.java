package io.github.ooboomberoo.precaststructure.structure;

import io.github.ooboomberoo.precaststructure.registry.ModBlocks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class StructureFrameDetector {
    private static final int MIN_PLATFORM_SIZE = 3;

    private StructureFrameDetector() {
    }

    public static ScanResult detect(Level level, BlockPos scannerPos) {
        int width = countRun(level, scannerPos, 1, 0);
        int depth = countRun(level, scannerPos, 0, 1);
        if (width < MIN_PLATFORM_SIZE || depth < MIN_PLATFORM_SIZE) {
            return ScanResult.error(Component.translatable("message.precaststructure.invalid_platform"));
        }

        if (!hasCompleteFloor(level, scannerPos, width, depth)) {
            return ScanResult.error(Component.translatable("message.precaststructure.invalid_floor"));
        }

        if (!hasFenceRing(level, scannerPos, width, depth)) {
            return ScanResult.error(Component.translatable("message.precaststructure.invalid_fence"));
        }

        BlockPos pillarBase = scannerPos.offset(width, 0, depth);
        int height = countPillar(level, pillarBase);
        if (height < 1) {
            return ScanResult.error(Component.translatable("message.precaststructure.invalid_height"));
        }

        return ScanResult.success(new StructureFrame(scannerPos.offset(2, 1, 2), new BlockPos(width - 2, height, depth - 2)));
    }

    private static int countRun(Level level, BlockPos scannerPos, int xStep, int zStep) {
        int count = 0;
        while (isPlatform(level.getBlockState(scannerPos.offset((count + 1) * xStep, 0, (count + 1) * zStep)))) {
            count++;
        }
        return count;
    }

    private static boolean hasCompleteFloor(Level level, BlockPos scannerPos, int width, int depth) {
        for (int x = 1; x <= width; x++) {
            for (int z = 1; z <= depth; z++) {
                if (!isPlatform(level.getBlockState(scannerPos.offset(x, 0, z)))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasFenceRing(Level level, BlockPos scannerPos, int width, int depth) {
        for (int x = 1; x <= width; x++) {
            for (int z = 1; z <= depth; z++) {
                if (x != 1 && x != width && z != 1 && z != depth) {
                    continue;
                }
                if (x == width && z == depth) {
                    continue;
                }
                BlockState state = level.getBlockState(scannerPos.offset(x, 1, z));
                if (!state.is(ModBlocks.PERIMETER_FENCE.get())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int countPillar(Level level, BlockPos pillarBase) {
        int height = 0;
        while (level.getBlockState(pillarBase.above(height + 1)).is(ModBlocks.METAL_SCAFFOLD.get())) {
            height++;
        }
        return height;
    }

    private static boolean isPlatform(BlockState state) {
        return state.is(ModBlocks.PLATFORM_FLOOR.get());
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
