package io.github.ooboomberoo.precaststructure.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ooboomberoo.precaststructure.structure.special.InventoryNbt;
import io.github.ooboomberoo.precaststructure.structure.special.SpecialBlockHandler;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Soft Create special-block module: schematic material costs (encased shaft → shaft), bracket
 * extras, inventory stripping that keeps machine config/brackets, and kinetic hologram meshes.
 *
 * <p>Registered only when Create is loaded ({@link CreateCompat#registerSpecialHandlers()}).
 * Built-in handlers (beds, doors, chests) stay registered first so they win on shared vanilla blocks.
 */
public final class CreateSpecialHandler implements SpecialBlockHandler {
    private static final String CREATE_NAMESPACE = "create";

    @Override
    public boolean matches(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && CREATE_NAMESPACE.equals(id.getNamespace());
    }

    @Override
    public @Nullable CompoundTag sanitizeCapturedNbt(BlockState state, @Nullable CompoundTag nbt) {
        // Create's NBTProcessors already ran during capture; strip common inventory keys as a
        // second pass while leaving Bracket / filter / cog settings intact.
        return InventoryNbt.stripContainerContents(nbt);
    }

    @Override
    public boolean mergeRequirements(
        BlockState state,
        @Nullable CompoundTag nbt,
        Map<Item, Integer> requirements,
        HolderLookup.Provider registries
    ) {
        if (!CreateCompat.tryMergeRequirements(state, requirements)) {
            Item item = state.getBlock().asItem();
            if (item != Items.AIR) {
                requirements.merge(item, 1, Integer::sum);
            }
        }
        Item bracket = CreateCompat.bracketItem(nbt, registries);
        if (bracket != null) {
            requirements.merge(bracket, 1, Integer::sum);
        }
        return true;
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
        // Virtual ModelData + kinetic BER extras (millstone cog, mixer, brackets, …).
        CreateCompatClient.renderSingleBlock(
            dispatcher, state, poseStack, bufferSource, packedLight, packedOverlay, nbt
        );
        return true;
    }
}
