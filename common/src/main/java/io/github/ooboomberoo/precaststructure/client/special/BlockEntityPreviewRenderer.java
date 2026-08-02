package io.github.ooboomberoo.precaststructure.client.special;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a temporary block entity through its vanilla BER (chests, beds, shulkers, …)
 * for hologram / solid previews. Callers must supply a {@link MultiBufferSource} that preserves
 * entity vertex formats — remapping them onto the block-atlas hologram layer breaks UVs.
 *
 * <p>Beds are special: vanilla only attaches a BE to the head, but {@link net.minecraft.client.renderer.blockentity.BedRenderer}
 * draws a single half per BE based on {@code PART}. Preview entities are created for both halves
 * so the foot piece is not missing from holograms.
 */
public final class BlockEntityPreviewRenderer {
    private BlockEntityPreviewRenderer() {
    }

    /**
     * @return {@code true} if a BER was invoked (even if it drew nothing)
     */
    public static boolean render(
        BlockState state,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay,
        @Nullable CompoundTag nbt
    ) {
        BlockEntity blockEntity = createPreviewEntity(state);
        if (blockEntity == null) {
            return false;
        }

        BlockEntityRenderer<BlockEntity> renderer = Minecraft.getInstance()
            .getBlockEntityRenderDispatcher()
            .getRenderer(blockEntity);
        if (renderer == null) {
            return false;
        }

        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }

        if (nbt != null && !nbt.isEmpty()) {
            try {
                blockEntity.loadWithComponents(nbt.copy(), level.registryAccess());
            } catch (RuntimeException ignored) {
                // Draw the default empty BE rather than aborting the preview.
            }
        }

        blockEntity.setLevel(level);
        try {
            renderer.render(blockEntity, 0.0F, poseStack, bufferSource, packedLight, packedOverlay);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            blockEntity.setLevel(null);
        }
    }

    /**
     * Builds a transient BE for preview. Beds construct {@link BedBlockEntity} for both head and
     * foot — {@link BedBlock#newBlockEntity} returns null for the foot.
     */
    private static @Nullable BlockEntity createPreviewEntity(BlockState state) {
        if (state.getBlock() instanceof BedBlock) {
            return new BedBlockEntity(BlockPos.ZERO, state);
        }
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
            return null;
        }
        try {
            return entityBlock.newBlockEntity(BlockPos.ZERO, state);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
