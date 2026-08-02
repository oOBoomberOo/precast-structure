package io.github.ooboomberoo.precaststructure.structure.special;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ooboomberoo.precaststructure.client.special.BlockEntityPreviewRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Entity-model containers (chest / ender chest / shulker). Block models are particle-only cubes
 * that look broken under the hologram block-atlas shader — draw the BER with its entity atlas
 * instead, and always strip inventory / loot from blueprint NBT.
 *
 * <p>Depth and color both use the BER mesh (not a stone proxy) so translucent hologram passes
 * depth-test against the real exterior and do not reveal hollow interior faces.
 */
public final class ChestSpecialHandler implements SpecialBlockHandler {
    @Override
    public boolean matches(BlockState state) {
        return state.getBlock() instanceof ChestBlock
            || state.getBlock() instanceof EnderChestBlock
            || state.getBlock() instanceof ShulkerBoxBlock;
    }

    @Override
    public @Nullable CompoundTag sanitizeCapturedNbt(BlockState state, @Nullable CompoundTag nbt) {
        return InventoryNbt.stripContainerContents(nbt);
    }

    @Override
    public boolean render(
        BlockRenderDispatcher dispatcher,
        BlockState state,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay,
        @Nullable CompoundTag nbt,
        RenderMode mode
    ) {
        // Skip particle-cube block model; BER uses FACING and the entity chest atlas.
        return BlockEntityPreviewRenderer.render(
            state, poseStack, bufferSource, packedLight, packedOverlay, nbt
        );
    }
}
