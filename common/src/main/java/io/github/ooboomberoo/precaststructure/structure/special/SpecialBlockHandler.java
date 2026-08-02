package io.github.ooboomberoo.precaststructure.structure.special;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Pluggable handling for blocks that need non-default blueprint, material, or hologram behavior
 * (beds, multi-block halves, Create kinetics, …).
 *
 * <p>Register instances through {@link SpecialBlockHandlers#register}. Core capture / placement /
 * hologram loops discover handlers via the registry — do not patch those call sites for each
 * new block type.
 *
 * <p>Container inventories are emptied in-world during capture
 * ({@link io.github.ooboomberoo.precaststructure.structure.ContainerCapture}); do not strip item
 * keys from NBT in {@link #sanitizeCapturedNbt}. Placement may still strip legacy keys via
 * {@link SpecialBlockHandlers#sanitizePlacement}.
 */
public interface SpecialBlockHandler {
    boolean matches(BlockState state);

    /**
     * Serialize this block entity for a blueprint. Default is vanilla
     * {@code saveWithFullMetadata} (coords stripped). Override for soft-compat processors
     * (e.g. Create NBTProcessors). Inventories should already be empty via
     * {@link io.github.ooboomberoo.precaststructure.structure.ContainerCapture}.
     */
    default @Nullable CompoundTag captureBlockEntityNbt(Level level, BlockEntity blockEntity) {
        return SpecialBlockHandlers.saveBlockEntityNbt(level, blockEntity);
    }

    /**
     * Load blueprint NBT onto a placed block entity. Default is vanilla
     * {@code loadWithComponents} plus a neighbour update. Override for soft-compat processors.
     */
    default void applyBlockEntityNbt(
        Level level,
        BlockEntity blockEntity,
        BlockState placedState,
        @Nullable CompoundTag nbt
    ) {
        SpecialBlockHandlers.loadBlockEntityNbt(level, blockEntity, placedState, nbt);
    }

    /**
     * After raw block-entity capture. Prefer leaving NBT alone — inventories should already be
     * empty from {@link io.github.ooboomberoo.precaststructure.structure.ContainerCapture}.
     */
    default @Nullable CompoundTag sanitizeCapturedNbt(BlockState state, @Nullable CompoundTag nbt) {
        return nbt;
    }

    /**
     * Normalize block state on capture / hologram / place when needed. Content flags such as
     * lectern {@code has_book} should already match emptied inventories after capture.
     */
    default BlockState sanitizeCapturedState(BlockState state) {
        return state;
    }

    /** Before applying block-entity NBT on place/deploy (also covers old blueprints). */
    default @Nullable CompoundTag sanitizePlacementNbt(BlockState state, @Nullable CompoundTag nbt) {
        return sanitizeCapturedNbt(state, nbt);
    }

    default BlockState sanitizePlacementState(BlockState state) {
        return sanitizeCapturedState(state);
    }

    /**
     * Rotate nested block-state data stored in BE NBT when the blueprint is rotated for capture,
     * placement, or preview (e.g. Create brackets). Default leaves NBT unchanged.
     */
    default @Nullable CompoundTag transformNbt(
        BlockState state,
        @Nullable CompoundTag nbt,
        Rotation rotation,
        HolderLookup.Provider registries
    ) {
        return nbt;
    }

    /**
     * Material contribution for this block part relative to {@link net.minecraft.world.level.block.Block#asItem()}.
     * Empty = decline via {@link #mergeRequirements} (caller uses default asItem); {@code 0} skips this half;
     * {@code N} costs N of {@code asItem()}.
     */
    default OptionalInt materialUnits(BlockState state) {
        return OptionalInt.empty();
    }

    /**
     * Merge this block's printer/tooltip material costs.
     *
     * @return {@code true} if this handler fully determined costs (including zero); {@code false} to use
     *     the default one-{@code asItem()} path
     */
    default boolean mergeRequirements(
        BlockState state,
        @Nullable CompoundTag nbt,
        Map<Item, Integer> requirements,
        HolderLookup.Provider registries
    ) {
        OptionalInt units = materialUnits(state);
        if (units.isEmpty()) {
            return false;
        }
        int n = units.getAsInt();
        if (n > 0) {
            Item item = state.getBlock().asItem();
            if (item != Items.AIR) {
                requirements.merge(item, n, Integer::sum);
            }
        }
        return true;
    }

    /**
     * Whether scan / deploy / placement ghosts should draw this part.
     * Override only to skip a half that must stay in the blueprint for placement but has no
     * distinct mesh (beds draw both halves via BER — do not skip the foot).
     */
    default boolean shouldRenderPreview(BlockState state) {
        return true;
    }

    /**
     * Client render override. Return {@code true} if this handler fully drew the block
     * (caller skips default {@code renderSingleBlock}).
     */
    default boolean render(
        BlockRenderDispatcher dispatcher,
        BlockState state,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay,
        @Nullable CompoundTag nbt,
        RenderMode mode
    ) {
        return false;
    }

    enum RenderMode {
        /** Translucent hologram color pass (entity formats must keep their atlas). */
        HOLOGRAM,
        /** Depth prepass only. */
        HOLOGRAM_DEPTH,
        /** Opaque / fullbright solid mesh (scan-below / item preview). */
        SOLID
    }
}
