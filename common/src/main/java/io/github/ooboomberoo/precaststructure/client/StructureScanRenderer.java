package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ooboomberoo.precaststructure.block.StructureScannerBlock;
import io.github.ooboomberoo.precaststructure.block.entity.StructureScannerBlockEntity;
import io.github.ooboomberoo.precaststructure.client.StructureHologramRenderer.Part;
import io.github.ooboomberoo.precaststructure.compat.SableCompatClient;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import io.github.ooboomberoo.precaststructure.structure.StructureBlockInfo;
import io.github.ooboomberoo.precaststructure.structure.StructurePlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

        List<StructureScannerBlockEntity> scanners = new ArrayList<>();
        for (StructureScannerBlockEntity scanner : StructureScannerBlockEntity.clientActiveScans()) {
            if (scanner.isScanning() && scanner.getGhostBlueprint() != null && !scanner.getGhostBlueprint().blocks().isEmpty()) {
                scanners.add(scanner);
            }
        }
        if (scanners.isEmpty()) {
            return;
        }

        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(768 * 1024)) {
            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(byteBuffer);

            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);

            for (StructureScannerBlockEntity scanner : scanners) {
                Vec3 plotOrigin = SableCompatClient.plotOrigin(scanner, partialTick);
                poseStack.pushPose();
                SableCompatClient.applyCameraAndSubLevelTransform(poseStack, scanner, cameraPosition, partialTick);
                renderSolids(poseStack, bufferSource, dispatcher, level, scanner, partialTick, plotOrigin);
                poseStack.popPose();
            }
            bufferSource.endBatch();

            if (!ModShaders.useCustomHologramShader()) {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.9F);
            }

            for (StructureScannerBlockEntity scanner : scanners) {
                List<Part> hologramParts = new ArrayList<>();
                collectHologramParts(scanner, partialTick, hologramParts);
                if (hologramParts.isEmpty()) {
                    continue;
                }
                Vec3 plotOrigin = SableCompatClient.plotOrigin(scanner, partialTick);
                poseStack.pushPose();
                SableCompatClient.applyCameraAndSubLevelTransform(poseStack, scanner, cameraPosition, partialTick);
                if (ModRenderTypes.useHologramDepthPrepass()) {
                    RenderSystem.depthMask(true);
                    RenderSystem.colorMask(false, false, false, false);
                    StructureHologramRenderer.renderPassLocal(
                        poseStack, cameraPosition, bufferSource, dispatcher, hologramParts, true, plotOrigin
                    );
                    bufferSource.endBatch();

                    RenderSystem.colorMask(true, true, true, true);
                    RenderSystem.depthMask(false);
                    StructureHologramRenderer.renderPassLocal(
                        poseStack, cameraPosition, bufferSource, dispatcher, hologramParts, false, plotOrigin
                    );
                    bufferSource.endBatch();
                } else {
                    RenderSystem.colorMask(true, true, true, true);
                    RenderSystem.depthMask(true);
                    StructureHologramRenderer.renderPassLocal(
                        poseStack, cameraPosition, bufferSource, dispatcher, hologramParts, false, plotOrigin
                    );
                    bufferSource.endBatch();
                }
                poseStack.popPose();
            }

            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            // Scan plane / bounds must read through ship meshes; depth would hide them inside wool.
            RenderSystem.disableDepthTest();
            for (StructureScannerBlockEntity scanner : scanners) {
                Vec3 plotOrigin = SableCompatClient.plotOrigin(scanner, partialTick);
                poseStack.pushPose();
                SableCompatClient.applyCameraAndSubLevelTransform(poseStack, scanner, cameraPosition, partialTick);
                renderScanOverlay(poseStack, bufferSource, scanner, partialTick, plotOrigin);
                poseStack.popPose();
            }
            bufferSource.endBatch();
            RenderSystem.enableDepthTest();
        }
    }

    private static void renderSolids(
        PoseStack poseStack,
        MultiBufferSource.BufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        Level level,
        StructureScannerBlockEntity scanner,
        float partialTick,
        Vec3 plotOrigin
    ) {
        StructureBlueprint ghosts = scanner.getGhostBlueprint();
        if (ghosts == null || ghosts.blocks().isEmpty()) {
            return;
        }

        float scanY = scanner.getScanLineY(partialTick);
        BlockPos origin = scanner.getScanOrigin();
        BlockPos scanSize = scanner.getScanSize();
        Direction scannerFacing = scanner.getBlockState().getValue(StructureScannerBlock.FACING);

        for (StructureBlockInfo block : ghosts.blocks()) {
            BlockPos worldPos = StructurePlacement.localToScanWorld(origin, scanSize, scannerFacing, block.offset());
            BlockState state = StructurePlacement.localToScanWorldState(block.state(), scannerFacing);
            BoundsY bounds = blockBoundsY(state, worldPos);

            boolean fullyBelow = bounds.maxY() <= scanY + CLIP_EPSILON;
            boolean intersectsOrBelow = bounds.minY() < scanY - CLIP_EPSILON;
            if (!fullyBelow && !intersectsOrBelow) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(
                worldPos.getX() - plotOrigin.x,
                worldPos.getY() - plotOrigin.y,
                worldPos.getZ() - plotOrigin.z
            );
            Float localClipY = fullyBelow ? null : scanY - worldPos.getY();
            renderSolidMesh(
                poseStack,
                bufferSource,
                dispatcher,
                state,
                scanNbt(block, scannerFacing, level),
                worldPos,
                level,
                localClipY
            );
            poseStack.popPose();
        }
    }

    private static void collectHologramParts(StructureScannerBlockEntity scanner, float partialTick, List<Part> out) {
        StructureBlueprint ghosts = scanner.getGhostBlueprint();
        if (ghosts == null || ghosts.blocks().isEmpty()) {
            return;
        }

        Level level = scanner.getLevel();
        float scanY = scanner.getScanLineY(partialTick);
        BlockPos origin = scanner.getScanOrigin();
        BlockPos scanSize = scanner.getScanSize();
        Direction scannerFacing = scanner.getBlockState().getValue(StructureScannerBlock.FACING);
        for (StructureBlockInfo block : ghosts.blocks()) {
            BlockPos worldPos = StructurePlacement.localToScanWorld(origin, scanSize, scannerFacing, block.offset());
            BlockState state = StructurePlacement.localToScanWorldState(block.state(), scannerFacing);
            BoundsY bounds = blockBoundsY(state, worldPos);
            if (bounds.maxY() <= scanY + CLIP_EPSILON) {
                continue;
            }
            boolean fullyAbove = bounds.minY() >= scanY - CLIP_EPSILON;
            Float localClipY = fullyAbove ? null : scanY - worldPos.getY();
            out.add(new Part(worldPos, state, scanNbt(block, scannerFacing, level), localClipY, false));
        }
    }

    private static @Nullable net.minecraft.nbt.CompoundTag scanNbt(
        StructureBlockInfo block,
        Direction scannerFacing,
        @Nullable Level level
    ) {
        if (block.nbt() == null || level == null) {
            return block.nbt();
        }
        return io.github.ooboomberoo.precaststructure.compat.CreateCompat.transformNbt(
            block.nbt(),
            StructurePlacement.rotationFor(StructurePlacement.scanForward(scannerFacing)),
            level.registryAccess()
        );
    }

    private static void renderScanOverlay(
        PoseStack poseStack,
        MultiBufferSource.BufferSource bufferSource,
        StructureScannerBlockEntity scanner,
        float partialTick,
        Vec3 plotOrigin
    ) {
        float scanY = scanner.getScanLineY(partialTick);
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        renderScanPlane(
            poseStack,
            lines,
            scanner.getScanOrigin(),
            scanner.getScanSize(),
            scanY,
            scanner.getScanProgress(partialTick),
            plotOrigin
        );
    }

    private static void renderSolidMesh(
        PoseStack poseStack,
        MultiBufferSource.BufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        BlockState state,
        @Nullable net.minecraft.nbt.CompoundTag nbt,
        BlockPos worldPos,
        Level level,
        @Nullable Float localClipY
    ) {
        // Plot-storage light on Sable ships is often zero; ghosts must stay readable.
        int light = LightTexture.FULL_BRIGHT;
        MultiBufferSource source;
        if (localClipY == null) {
            source = bufferSource;
        } else {
            float clipY = localClipY;
            source = renderType -> new StructureHologramRenderer.PlaneClipVertexConsumer(bufferSource.getBuffer(renderType), clipY, true);
        }
        io.github.ooboomberoo.precaststructure.compat.CreateCompatClient.renderSingleBlock(
            dispatcher,
            state,
            poseStack,
            source,
            light,
            OverlayTexture.NO_OVERLAY,
            nbt
        );
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
        float progress,
        Vec3 plotOrigin
    ) {
        double minX = origin.getX() - plotOrigin.x;
        double minZ = origin.getZ() - plotOrigin.z;
        double maxX = origin.getX() + Math.max(1, size.getX()) - plotOrigin.x;
        double maxZ = origin.getZ() + Math.max(1, size.getZ()) - plotOrigin.z;
        double bottom = origin.getY() - plotOrigin.y;
        double top = origin.getY() + Math.max(1, size.getY()) - plotOrigin.y;
        double planeY = scanY - plotOrigin.y;
        float pulse = 0.7F + 0.3F * Mth.sin(scanY * 8.0F + progress * 20.0F);

        LevelRenderer.renderLineBox(poseStack, lines, minX, bottom, minZ, maxX, top, maxZ, LINE_RED, LINE_GREEN, LINE_BLUE, 0.55F);
        LevelRenderer.renderLineBox(
            poseStack,
            lines,
            minX,
            planeY - PLANE_THICKNESS * 0.5F,
            minZ,
            maxX,
            planeY + PLANE_THICKNESS * 0.5F,
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
