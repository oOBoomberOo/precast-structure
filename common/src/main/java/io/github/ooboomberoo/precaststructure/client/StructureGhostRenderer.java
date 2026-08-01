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
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class StructureGhostRenderer {
    private static final int GHOST_ALPHA_PLACEABLE = 110;
    private static final int GHOST_ALPHA_BLOCKED = 55;
    private static final float PLACEABLE_RED = 0.35F;
    private static final float PLACEABLE_GREEN = 1.0F;
    private static final float PLACEABLE_BLUE = 0.45F;
    private static final float BLOCKED_RED = 1.0F;
    private static final float BLOCKED_GREEN = 0.2F;
    private static final float BLOCKED_BLUE = 0.2F;

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
        Direction facing = player.getDirection();
        BlockPos origin = StructurePlacement.resolveOrigin(new UseOnContext(player, player.getUsedItemHand(), hitResult));
        boolean placeable = StructurePlacement.firstBlockedPosition(level, origin, blueprint, facing).isEmpty();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();

        float red = placeable ? PLACEABLE_RED : BLOCKED_RED;
        float green = placeable ? PLACEABLE_GREEN : BLOCKED_GREEN;
        float blue = placeable ? PLACEABLE_BLUE : BLOCKED_BLUE;
        int alpha = placeable ? GHOST_ALPHA_PLACEABLE : GHOST_ALPHA_BLOCKED;

        for (StructureBlockInfo block : blueprint.blocks()) {
            BlockPos blockPos = origin.offset(StructurePlacement.transformOffset(block.offset(), blueprint, facing));
            BlockState state = StructurePlacement.transformState(block.state(), facing);

            poseStack.pushPose();
            poseStack.translate(blockPos.getX() - cameraPosition.x, blockPos.getY() - cameraPosition.y, blockPos.getZ() - cameraPosition.z);
            MultiBufferSource ghostSource = renderType -> new GhostVertexConsumer(bufferSource.getBuffer(RenderType.translucent()), alpha);
            dispatcher.renderSingleBlock(state, poseStack, ghostSource, 0x00F000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);

            VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
            VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (shape.isEmpty()) {
                LevelRenderer.renderLineBox(poseStack, lines, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, red, green, blue, 0.9F);
            } else {
                for (AABB aabb : shape.toAabbs()) {
                    LevelRenderer.renderLineBox(
                        poseStack,
                        lines,
                        (float) aabb.minX,
                        (float) aabb.minY,
                        (float) aabb.minZ,
                        (float) aabb.maxX,
                        (float) aabb.maxY,
                        (float) aabb.maxZ,
                        red,
                        green,
                        blue,
                        0.9F
                    );
                }
            }
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
