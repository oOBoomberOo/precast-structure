package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import io.github.ooboomberoo.precaststructure.structure.BlueprintItemData;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import io.github.ooboomberoo.precaststructure.structure.StructureBlockInfo;
import io.github.ooboomberoo.precaststructure.structure.StructurePlacement;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class StructureGhostRenderer {
    private StructureGhostRenderer() {
    }

    public static void render(PoseStack poseStack, Vec3 cameraPosition, MultiBufferSource.BufferSource bufferSource) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null || !(minecraft.hitResult instanceof BlockHitResult hitResult) || minecraft.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        ItemStack stack = getPreviewStack(player);
        if (stack.isEmpty()) {
            return;
        }

        Optional<StructureBlueprint> optional = BlueprintItemData.read(stack, level.registryAccess());
        if (optional.isEmpty()) {
            return;
        }

        StructureBlueprint blueprint = optional.get();
        BlockPos origin = level.getBlockState(hitResult.getBlockPos()).canBeReplaced() ? hitResult.getBlockPos() : hitResult.getBlockPos().relative(hitResult.getDirection());
        boolean placeable = StructurePlacement.firstBlockedPosition(level, origin, blueprint).isEmpty();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();

        for (StructureBlockInfo block : blueprint.blocks()) {
            BlockPos blockPos = origin.offset(block.offset());
            poseStack.pushPose();
            poseStack.translate(blockPos.getX() - cameraPosition.x, blockPos.getY() - cameraPosition.y, blockPos.getZ() - cameraPosition.z);
            MultiBufferSource ghostSource = renderType -> new GhostVertexConsumer(bufferSource.getBuffer(RenderType.translucent()), placeable ? 110 : 55);
            dispatcher.renderSingleBlock(block.state(), poseStack, ghostSource, 0x00F000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
            float red = placeable ? 0.35F : 1.0F;
            float green = placeable ? 1.0F : 0.2F;
            float blue = placeable ? 0.45F : 0.2F;
            LevelRenderer.renderLineBox(poseStack, lines, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, red, green, blue, 0.9F);
            poseStack.popPose();
        }

        bufferSource.endBatch(RenderType.translucent());
        bufferSource.endBatch(RenderType.lines());
    }

    private static ItemStack getPreviewStack(Player player) {
        if (player.getMainHandItem().is(ModItems.PRECAST_STRUCTURE.get())) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().is(ModItems.PRECAST_STRUCTURE.get())) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }
}
