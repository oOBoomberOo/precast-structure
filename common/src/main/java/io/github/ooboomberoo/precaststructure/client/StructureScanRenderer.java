package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ooboomberoo.precaststructure.block.entity.StructureScannerBlockEntity;
import io.github.ooboomberoo.precaststructure.client.StructureHologramRenderer.Part;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import io.github.ooboomberoo.precaststructure.structure.StructureBlockInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
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
 * Scan ghosts clipped precisely to the moving scan plane:
 * solid block mesh below the plane, textured hologram mesh above it.
 */
public final class StructureScanRenderer {
    private static final float LINE_RED = 0.2F;
    private static final float LINE_GREEN = 0.95F;
    private static final float LINE_BLUE = 1.0F;
    private static final float PLANE_THICKNESS = 0.04F;
    private static final float CLIP_EPSILON = StructureHologramRenderer.CLIP_EPSILON;

    private StructureScanRenderer() {
    }

    public static void render(PoseStack poseStack, Vec3 cameraPosition, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            return;
        }

        boolean any = false;
        for (StructureScannerBlockEntity scanner : StructureScannerBlockEntity.clientActiveScans()) {
            if (scanner.isScanning() && scanner.getGhostBlueprint() != null && !scanner.getGhostBlueprint().blocks().isEmpty()) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }

        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        ByteBufferBuilder byteBuffer = new ByteBufferBuilder(768 * 1024);
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(byteBuffer);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        for (StructureScannerBlockEntity scanner : StructureScannerBlockEntity.clientActiveScans()) {
            renderSolids(poseStack, cameraPosition, bufferSource, dispatcher, level, scanner, partialTick);
        }
        bufferSource.endBatch();

        if (ModShaders.getScanHologram() != null) {
            List<Part> hologramParts = new ArrayList<>();
            for (StructureScannerBlockEntity scanner : StructureScannerBlockEntity.clientActiveScans()) {
                collectHologramParts(scanner, partialTick, hologramParts);
            }

            RenderSystem.depthMask(true);
            StructureHologramRenderer.renderPass(poseStack, cameraPosition, bufferSource, dispatcher, hologramParts, true);
            bufferSource.endBatch();

            RenderSystem.depthMask(false);
            StructureHologramRenderer.renderPass(poseStack, cameraPosition, bufferSource, dispatcher, hologramParts, false);
            bufferSource.endBatch();

            RenderSystem.depthMask(true);
            for (StructureScannerBlockEntity scanner : StructureScannerBlockEntity.clientActiveScans()) {
                renderScanOverlay(poseStack, cameraPosition, bufferSource, scanner, partialTick);
            }
            bufferSource.endBatch();
        }
    }

    private static void renderSolids(
        PoseStack poseStack,
        Vec3 cameraPosition,
        MultiBufferSource.BufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        Level level,
        StructureScannerBlockEntity scanner,
        float partialTick
    ) {
        if (!scanner.isScanning()) {
            return;
        }
        StructureBlueprint ghosts = scanner.getGhostBlueprint();
        if (ghosts == null || ghosts.blocks().isEmpty()) {
            return;
        }

        float scanY = scanner.getScanLineY(partialTick);
        BlockPos origin = scanner.getScanOrigin();

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

        for (StructureBlockInfo block : ghosts.blocks()) {
            BlockPos worldPos = origin.offset(block.offset());
            BlockState state = block.state();
            BoundsY bounds = blockBoundsY(state, worldPos);

            boolean fullyBelow = bounds.maxY() <= scanY + CLIP_EPSILON;
            boolean intersectsOrBelow = bounds.minY() < scanY - CLIP_EPSILON;
            if (!fullyBelow && !intersectsOrBelow) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(worldPos.getX(), worldPos.getY(), worldPos.getZ());
            Float localClipY = fullyBelow ? null : scanY - worldPos.getY();
            renderSolidMesh(poseStack, bufferSource, dispatcher, state, worldPos, level, localClipY);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static void collectHologramParts(StructureScannerBlockEntity scanner, float partialTick, List<Part> out) {
        if (!scanner.isScanning()) {
            return;
        }
        StructureBlueprint ghosts = scanner.getGhostBlueprint();
        if (ghosts == null || ghosts.blocks().isEmpty()) {
            return;
        }

        float scanY = scanner.getScanLineY(partialTick);
        BlockPos origin = scanner.getScanOrigin();
        for (StructureBlockInfo block : ghosts.blocks()) {
            BlockPos worldPos = origin.offset(block.offset());
            BlockState state = block.state();
            BoundsY bounds = blockBoundsY(state, worldPos);
            if (bounds.maxY() <= scanY + CLIP_EPSILON) {
                continue;
            }
            boolean fullyAbove = bounds.minY() >= scanY - CLIP_EPSILON;
            Float localClipY = fullyAbove ? null : scanY - worldPos.getY();
            out.add(new Part(worldPos, state, localClipY, false));
        }
    }

    private static void renderScanOverlay(
        PoseStack poseStack,
        Vec3 cameraPosition,
        MultiBufferSource.BufferSource bufferSource,
        StructureScannerBlockEntity scanner,
        float partialTick
    ) {
        if (!scanner.isScanning()) {
            return;
        }
        float scanY = scanner.getScanLineY(partialTick);
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        renderScanPlane(poseStack, lines, scanner.getScanOrigin(), scanner.getScanSize(), scanY, scanner.getScanProgress(partialTick));
        poseStack.popPose();
    }

    private static void renderSolidMesh(
        PoseStack poseStack,
        MultiBufferSource.BufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        BlockState state,
        BlockPos worldPos,
        Level level,
        @Nullable Float localClipY
    ) {
        int light = LevelRenderer.getLightColor(level, worldPos);
        MultiBufferSource source;
        if (localClipY == null) {
            source = bufferSource;
        } else {
            float clipY = localClipY;
            source = renderType -> new StructureHologramRenderer.PlaneClipVertexConsumer(bufferSource.getBuffer(renderType), clipY, true);
        }
        dispatcher.renderSingleBlock(state, poseStack, source, light, OverlayTexture.NO_OVERLAY);
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

    private static void renderScanPlane(
        PoseStack poseStack,
        VertexConsumer lines,
        BlockPos origin,
        BlockPos size,
        float scanY,
        float progress
    ) {
        double minX = origin.getX();
        double minZ = origin.getZ();
        double maxX = origin.getX() + Math.max(1, size.getX());
        double maxZ = origin.getZ() + Math.max(1, size.getZ());
        double bottom = origin.getY();
        double top = origin.getY() + Math.max(1, size.getY());
        float pulse = 0.7F + 0.3F * Mth.sin(scanY * 8.0F + progress * 20.0F);

        LevelRenderer.renderLineBox(poseStack, lines, minX, bottom, minZ, maxX, top, maxZ, LINE_RED, LINE_GREEN, LINE_BLUE, 0.15F);
        LevelRenderer.renderLineBox(
            poseStack,
            lines,
            minX,
            scanY - PLANE_THICKNESS * 0.5F,
            minZ,
            maxX,
            scanY + PLANE_THICKNESS * 0.5F,
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
