package io.github.ooboomberoo.precaststructure.structure;

import io.github.ooboomberoo.precaststructure.config.ModConfig;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * One in-progress prefab deploy: rising plane reveals solid ghosts below and holograms above.
 * Timing matches scanning ({@code max(80, height * 16)}), but the plane moves bottom → top.
 * Real blocks are written only when the animation completes.
 */
public final class StructureDeployment {
    private final UUID id;
    private final BlockPos origin;
    private final Direction facing;
    private final StructureBlueprint blueprint;
    private final BlockPos boundsMin;
    private final BlockPos boundsMax;
    private final long startGameTime;
    private final int duration;
    private boolean placed;

    public StructureDeployment(
        UUID id,
        BlockPos origin,
        Direction facing,
        StructureBlueprint blueprint,
        long startGameTime,
        int duration
    ) {
        this.id = id;
        this.origin = origin.immutable();
        this.facing = facing.getAxis().isVertical() ? Direction.NORTH : facing;
        this.blueprint = blueprint;
        this.startGameTime = startGameTime;
        this.duration = Math.max(1, duration);
        this.placed = false;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (StructureBlockInfo block : blueprint.blocks()) {
            BlockPos worldPos = origin.offset(StructurePlacement.transformOffset(block.offset(), blueprint, this.facing));
            minX = Math.min(minX, worldPos.getX());
            minY = Math.min(minY, worldPos.getY());
            minZ = Math.min(minZ, worldPos.getZ());
            maxX = Math.max(maxX, worldPos.getX());
            maxY = Math.max(maxY, worldPos.getY());
            maxZ = Math.max(maxZ, worldPos.getZ());
        }
        if (blueprint.blocks().isEmpty()) {
            this.boundsMin = origin.immutable();
            this.boundsMax = origin.immutable();
        } else {
            this.boundsMin = new BlockPos(minX, minY, minZ);
            this.boundsMax = new BlockPos(maxX, maxY, maxZ);
        }
    }

    public static int durationForHeight(int height) {
        ModConfig.Deploy deploy = ModConfig.get().deploy;
        return Math.max(deploy.minTicks, Math.max(1, height) * deploy.ticksPerHeight);
    }

    public static StructureDeployment create(BlockPos origin, Direction facing, StructureBlueprint blueprint, long gameTime) {
        int height = Math.max(1, blueprint.size().getY());
        return new StructureDeployment(UUID.randomUUID(), origin, facing, blueprint, gameTime, durationForHeight(height));
    }

    public UUID id() {
        return id;
    }

    public BlockPos origin() {
        return origin;
    }

    public Direction facing() {
        return facing;
    }

    public StructureBlueprint blueprint() {
        return blueprint;
    }

    public BlockPos boundsMin() {
        return boundsMin;
    }

    public BlockPos boundsMax() {
        return boundsMax;
    }

    public long startGameTime() {
        return startGameTime;
    }

    public int duration() {
        return duration;
    }

    public AABB bounds() {
        return new AABB(
            boundsMin.getX(),
            boundsMin.getY(),
            boundsMin.getZ(),
            boundsMax.getX() + 1,
            boundsMax.getY() + 1,
            boundsMax.getZ() + 1
        );
    }

    public BlockPos planeSize() {
        return new BlockPos(
            boundsMax.getX() - boundsMin.getX() + 1,
            boundsMax.getY() - boundsMin.getY() + 1,
            boundsMax.getZ() - boundsMin.getZ() + 1
        );
    }

    public float getProgress(Level level, float partialTick) {
        if (duration <= 0) {
            return 1.0F;
        }
        float elapsed = (float) (level.getGameTime() - startGameTime) + partialTick;
        return Mth.clamp(elapsed / duration, 0.0F, 1.0F);
    }

    /** World Y of the deploy plane; progresses from the floor of the volume up to the top. */
    public float getDeployLineY(Level level, float partialTick) {
        float bottom = boundsMin.getY();
        float top = boundsMax.getY() + 1.0F;
        return Mth.lerp(getProgress(level, partialTick), bottom, top);
    }

    public boolean isComplete(Level level) {
        return level.getGameTime() - startGameTime >= duration;
    }

    public boolean hasPlaced() {
        return placed;
    }

    public void placeAll(Level level) {
        if (placed) {
            return;
        }
        StructurePlacement.place(level, origin, blueprint, facing);
        // Ensure temporary collision cubes never linger if a place was skipped.
        HologramCollision.clearBlueprint(level, origin, blueprint, facing);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            for (StructureBlockInfo block : blueprint.blocks()) {
                BlockPos worldPos = origin.offset(StructurePlacement.transformOffset(block.offset(), blueprint, facing));
                serverLevel.getChunkSource().getLightEngine().checkBlock(worldPos);
            }
        }
        placed = true;
    }
}
