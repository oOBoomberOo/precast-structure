package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ooboomberoo.precaststructure.structure.BlueprintItemData;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import io.github.ooboomberoo.precaststructure.structure.StructureBlockInfo;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public final class StructureItemRenderer {
    private StructureItemRenderer() {
    }

    public static void render(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        Optional<StructureBlueprint> optional = BlueprintItemData.read(stack, ClientRegistryAccess.getLookup());
        if (optional.isEmpty()) {
            dispatcher.renderSingleBlock(Blocks.STRUCTURE_BLOCK.defaultBlockState(), poseStack, bufferSource, light, overlay);
            return;
        }

        StructureBlueprint blueprint = optional.get();
        float maxSize = Math.max(1.0F, Math.max(Math.max(blueprint.size().getX(), blueprint.size().getY()), blueprint.size().getZ()));
        float scale = 0.85F / maxSize;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.05F, 0.5F);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-blueprint.size().getX() / 2.0F, 0.0F, -blueprint.size().getZ() / 2.0F);
        for (StructureBlockInfo block : blueprint.blocks()) {
            poseStack.pushPose();
            poseStack.translate(block.offset().getX(), block.offset().getY(), block.offset().getZ());
            dispatcher.renderSingleBlock(block.state(), poseStack, bufferSource, light, overlay);
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}
