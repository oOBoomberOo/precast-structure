package io.github.ooboomberoo.precaststructure.structure.special;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ooboomberoo.precaststructure.client.special.BlockEntityPreviewRenderer;
import java.util.OptionalInt;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.jetbrains.annotations.Nullable;

/**
 * Beds are two blocks; the BER draws one half per block entity based on {@code PART}. Both halves
 * get a preview BE (foot included) so holograms / scan ghosts show a complete bed. Only the head
 * contributes a material; both halves stay in the blueprint for correct placement and clear.
 */
public final class BedSpecialHandler implements SpecialBlockHandler {
    @Override
    public boolean matches(BlockState state) {
        return state.getBlock() instanceof BedBlock;
    }

    @Override
    public @Nullable CompoundTag sanitizeCapturedNbt(BlockState state, @Nullable CompoundTag nbt) {
        return InventoryNbt.stripContainerContents(nbt);
    }

    @Override
    public OptionalInt materialUnits(BlockState state) {
        return state.getValue(BedBlock.PART) == BedPart.HEAD ? OptionalInt.of(1) : OptionalInt.of(0);
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
        // Depth + color both use the real BER mesh so hollow interiors depth-test correctly.
        return BlockEntityPreviewRenderer.render(
            state, poseStack, bufferSource, packedLight, packedOverlay, nbt
        );
    }
}
