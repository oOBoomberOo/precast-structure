package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ooboomberoo.precaststructure.client.StructureHologramRenderer.Part;
import io.github.ooboomberoo.precaststructure.structure.StructureBlockInfo;
import io.github.ooboomberoo.precaststructure.structure.StructureDeployment;
import io.github.ooboomberoo.precaststructure.structure.StructureDeploymentManager;
import io.github.ooboomberoo.precaststructure.structure.StructurePlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Rising-plane deploy preview (reverse of scan): solid ghosts below the plane, holograms above.
 * Real blocks are written only when the deploy finishes.
 */
public final class StructureDeployRenderer {
    private static final float LINE_RED = 0.2F;
    private static final float LINE_GREEN = 0.95F;
    private static final float LINE_BLUE = 1.0F;
    private static final float PLANE_THICKNESS = 0.04F;
    private static final float CLIP_EPSILON = StructureHologramRenderer.CLIP_EPSILON;

    private StructureDeployRenderer() {
    }

    public static void render(PoseStack poseStack, Vec3 cameraPosition, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || StructureDeploymentManager.clientDeployments().isEmpty()) {
            return;
        }

        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(768 * 1024)) {
            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(byteBuffer);

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            // Sit slightly in front of just-placed blocks so the fullbright cover hides unlit geometry.
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-1.0F, -10.0F);

            for (StructureDeployment deployment : StructureDeploymentManager.clientDeployments()) {
                boolean finishing = isFinishingCover(level, deployment, partialTick);
                renderSolids(poseStack, cameraPosition, bufferSource, dispatcher, level, deployment, partialTick, finishing);
            }
            bufferSource.endBatch();

            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();

            List<Part> hologramParts = new ArrayList<>();
            for (StructureDeployment deployment : StructureDeploymentManager.clientDeployments()) {
                if (isFinishingCover(level, deployment, partialTick)) {
                    continue;
                }
                collectHologramParts(level, deployment, partialTick, hologramParts);
            }

        if (!ModShaders.useCustomHologramShader()) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.9F);
        }

        if (ModRenderTypes.useHologramDepthPrepass()) {
            RenderSystem.depthMask(true);
            StructureHologramRenderer.renderPass(poseStack, cameraPosition, bufferSource, dispatcher, hologramParts, true);
            bufferSource.endBatch();

            RenderSystem.depthMask(false);
            StructureHologramRenderer.renderPass(poseStack, cameraPosition, bufferSource, dispatcher, hologramParts, false);
            bufferSource.endBatch();
        } else {
            RenderSystem.depthMask(true);
            StructureHologramRenderer.renderPass(poseStack, cameraPosition, bufferSource, dispatcher, hologramParts, false);
            bufferSource.endBatch();
        }

        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            for (StructureDeployment deployment : StructureDeploymentManager.clientDeployments()) {
                if (isFinishingCover(level, deployment, partialTick)) {
                    continue;
                }
                renderDeployOverlay(poseStack, cameraPosition, bufferSource, level, deployment, partialTick);
            }
            bufferSource.endBatch();
        }
    }

    private static boolean isFinishingCover(Level level, StructureDeployment deployment, float partialTick) {
        return StructureDeploymentManager.clientIsFinishing(deployment.id())
            || deployment.getProgress(level, partialTick) >= 1.0F;
    }

    private static void renderSolids(
        PoseStack poseStack,
        Vec3 cameraPosition,
        MultiBufferSource.BufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        Level level,
        StructureDeployment deployment,
        float partialTick,
        boolean finishing
    ) {
        float deployY = finishing ? Float.POSITIVE_INFINITY : deployment.getDeployLineY(level, partialTick);
        BlockPos origin = deployment.origin();

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

        for (StructureBlockInfo block : deployment.blueprint().blocks()) {
            BlockPos worldPos = origin.offset(StructurePlacement.transformOffset(block.offset(), deployment.blueprint(), deployment.facing()));
            BlockState state = StructurePlacement.transformState(block.state(), deployment.facing());
            BoundsY bounds = blockBoundsY(state, worldPos);

            boolean fullyBelow = bounds.maxY() <= deployY + CLIP_EPSILON;
            boolean intersectsOrBelow = bounds.minY() < deployY - CLIP_EPSILON;
            if (!fullyBelow && !intersectsOrBelow) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(worldPos.getX(), worldPos.getY(), worldPos.getZ());
            Float localClipY = fullyBelow ? null : deployY - worldPos.getY();
            renderSolidMesh(poseStack, bufferSource, dispatcher, state, localClipY);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static void collectHologramParts(Level level, StructureDeployment deployment, float partialTick, List<Part> out) {
        float deployY = deployment.getDeployLineY(level, partialTick);
        BlockPos origin = deployment.origin();
        for (StructureBlockInfo block : deployment.blueprint().blocks()) {
            BlockPos worldPos = origin.offset(StructurePlacement.transformOffset(block.offset(), deployment.blueprint(), deployment.facing()));
            BlockState state = StructurePlacement.transformState(block.state(), deployment.facing());
            BoundsY bounds = blockBoundsY(state, worldPos);
            if (bounds.maxY() <= deployY + CLIP_EPSILON) {
                continue;
            }
            boolean fullyAbove = bounds.minY() >= deployY - CLIP_EPSILON;
            Float localClipY = fullyAbove ? null : deployY - worldPos.getY();
            out.add(new Part(worldPos, state, localClipY, false));
        }
    }

    private static void renderSolidMesh(
        PoseStack poseStack,
        MultiBufferSource.BufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        BlockState state,
        @Nullable Float localClipY
    ) {
        // Fullbright avoids the post-place lighting lag (ghosts would otherwise go dark for a beat).
        MultiBufferSource source;
        if (localClipY == null) {
            source = bufferSource;
        } else {
            float clipY = localClipY;
            source = renderType -> new StructureHologramRenderer.PlaneClipVertexConsumer(bufferSource.getBuffer(renderType), clipY, true);
        }
        dispatcher.renderSingleBlock(state, poseStack, source, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }

    private static void renderDeployOverlay(
        PoseStack poseStack,
        Vec3 cameraPosition,
        MultiBufferSource.BufferSource bufferSource,
        Level level,
        StructureDeployment deployment,
        float partialTick
    ) {
        float deployY = deployment.getDeployLineY(level, partialTick);
        float progress = deployment.getProgress(level, partialTick);
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        renderDeployPlane(poseStack, lines, deployment.boundsMin(), deployment.planeSize(), deployY, progress);
        poseStack.popPose();
    }

    private static BoundsY blockBoundsY(BlockState state, BlockPos worldPos) {
        double minY = worldPos.getY();
        double maxY = worldPos.getY() + 1.0;
        VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (!shape.isEmpty()) {
            AABB bounds = shape.bounds();
            minY = worldPos.getY() + bounds.minY;
            maxY = worldPos.getY() + bounds.maxY;
        }
        return new BoundsY(minY, maxY);
    }

    private static void renderDeployPlane(
        PoseStack poseStack,
        VertexConsumer lines,
        BlockPos origin,
        BlockPos size,
        float deployY,
        float progress
    ) {
        double minX = origin.getX();
        double minZ = origin.getZ();
        double maxX = origin.getX() + Math.max(1, size.getX());
        double maxZ = origin.getZ() + Math.max(1, size.getZ());
        double bottom = origin.getY();
        double top = origin.getY() + Math.max(1, size.getY());
        float pulse = 0.7F + 0.3F * Mth.sin(deployY * 8.0F + progress * 20.0F);

        LevelRenderer.renderLineBox(poseStack, lines, minX, bottom, minZ, maxX, top, maxZ, LINE_RED, LINE_GREEN, LINE_BLUE, 0.15F);
        LevelRenderer.renderLineBox(
            poseStack,
            lines,
            minX,
            deployY - PLANE_THICKNESS * 0.5F,
            minZ,
            maxX,
            deployY + PLANE_THICKNESS * 0.5F,
            maxZ,
            LINE_RED,
            LINE_GREEN,
            LINE_BLUE,
            0.95F * pulse
        );
    }

    private record BoundsY(double minY, double maxY) {
    }
}
