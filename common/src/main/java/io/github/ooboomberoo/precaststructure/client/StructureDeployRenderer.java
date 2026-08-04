package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ooboomberoo.precaststructure.client.HologramRenderSystem.Frame;
import io.github.ooboomberoo.precaststructure.client.HologramRenderSystem.Part;
import io.github.ooboomberoo.precaststructure.structure.StructureBlockInfo;
import io.github.ooboomberoo.precaststructure.structure.StructureDeployment;
import io.github.ooboomberoo.precaststructure.structure.StructureDeploymentManager;
import io.github.ooboomberoo.precaststructure.structure.StructurePlacement;
import io.github.ooboomberoo.precaststructure.structure.special.SpecialBlockHandlers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rising-plane deploy preview (reverse of scan): solid ghosts below the plane, holograms above.
 * Real blocks are written only when the deploy finishes.
 */
public final class StructureDeployRenderer {
  private static final float LINE_RED = 0.2F;
  private static final float LINE_GREEN = 0.95F;
  private static final float LINE_BLUE = 1.0F;
  private static final float PLANE_THICKNESS = 0.04F;
  private static final float CLIP_EPSILON = HologramRenderSystem.CLIP_EPSILON;

  private StructureDeployRenderer() {}

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
        Frame frame =
            HologramRenderSystem.pushWorldFrame(
                poseStack, cameraPosition, partialTick, deployment.origin());
        try {
          renderSolids(
              poseStack,
              bufferSource,
              dispatcher,
              level,
              deployment,
              partialTick,
              finishing,
              frame);
        } finally {
          poseStack.popPose();
        }
      }
      bufferSource.endBatch();

      RenderSystem.polygonOffset(0.0F, 0.0F);
      RenderSystem.disablePolygonOffset();

      for (StructureDeployment deployment : StructureDeploymentManager.clientDeployments()) {
        if (isFinishingCover(level, deployment, partialTick)) {
          continue;
        }
        List<Part> hologramParts = new ArrayList<>();
        collectHologramParts(level, deployment, partialTick, hologramParts);
        if (hologramParts.isEmpty()) {
          continue;
        }
        Frame frame =
            HologramRenderSystem.pushWorldFrame(
                poseStack, cameraPosition, partialTick, deployment.origin());
        try {
          HologramRenderSystem.renderFramed(
              poseStack,
              cameraPosition,
              bufferSource,
              dispatcher,
              frame,
              hologramParts,
              1.0F,
              1.0F,
              1.0F);
        } finally {
          poseStack.popPose();
        }
      }

      for (StructureDeployment deployment : StructureDeploymentManager.clientDeployments()) {
        if (isFinishingCover(level, deployment, partialTick)) {
          continue;
        }
        Frame frame =
            HologramRenderSystem.pushWorldFrame(
                poseStack, cameraPosition, partialTick, deployment.origin());
        try {
          renderDeployOverlay(poseStack, bufferSource, level, deployment, partialTick, frame);
        } finally {
          poseStack.popPose();
        }
      }
      bufferSource.endBatch();
    }
  }

  private static boolean isFinishingCover(
      Level level, StructureDeployment deployment, float partialTick) {
    return StructureDeploymentManager.clientIsFinishing(deployment.id())
        || deployment.getProgress(level, partialTick) >= 1.0F;
  }

  private static void renderSolids(
      PoseStack poseStack,
      MultiBufferSource.BufferSource bufferSource,
      BlockRenderDispatcher dispatcher,
      Level level,
      StructureDeployment deployment,
      float partialTick,
      boolean finishing,
      Frame frame) {
    float deployY =
        finishing ? Float.POSITIVE_INFINITY : deployment.getDeployLineY(level, partialTick);
    BlockPos origin = deployment.origin();
    Vec3 plotOrigin = frame.plotOrigin();

    for (StructureBlockInfo block : deployment.blueprint().blocks()) {
      BlockPos worldPos =
          origin.offset(
              StructurePlacement.transformOffset(
                  block.offset(), deployment.blueprint(), deployment.facing()));
      BlockState state = StructurePlacement.transformState(block.state(), deployment.facing());
      BoundsY bounds = blockBoundsY(state, worldPos);

      boolean fullyBelow = bounds.maxY() <= deployY + CLIP_EPSILON;
      boolean intersectsOrBelow = bounds.minY() < deployY - CLIP_EPSILON;
      if (!fullyBelow && !intersectsOrBelow) {
        continue;
      }

      poseStack.pushPose();
      poseStack.translate(
          worldPos.getX() - plotOrigin.x,
          worldPos.getY() - plotOrigin.y,
          worldPos.getZ() - plotOrigin.z);
      Float localClipY = fullyBelow ? null : deployY - worldPos.getY();
      HologramRenderSystem.renderSolid(
          poseStack,
          bufferSource,
          dispatcher,
          state,
          SpecialBlockHandlers.transformNbt(
              state,
              block.nbt(),
              StructurePlacement.rotationFor(deployment.facing()),
              level.registryAccess()),
          localClipY);
      poseStack.popPose();
    }
  }

  private static void collectHologramParts(
      Level level, StructureDeployment deployment, float partialTick, List<Part> out) {
    float deployY = deployment.getDeployLineY(level, partialTick);
    BlockPos origin = deployment.origin();
    for (StructureBlockInfo block : deployment.blueprint().blocks()) {
      BlockPos worldPos =
          origin.offset(
              StructurePlacement.transformOffset(
                  block.offset(), deployment.blueprint(), deployment.facing()));
      BlockState state = StructurePlacement.transformState(block.state(), deployment.facing());
      BoundsY bounds = blockBoundsY(state, worldPos);
      if (bounds.maxY() <= deployY + CLIP_EPSILON) {
        continue;
      }
      boolean fullyAbove = bounds.minY() >= deployY - CLIP_EPSILON;
      Float localClipY = fullyAbove ? null : deployY - worldPos.getY();
      out.add(
          new Part(
              worldPos,
              state,
              SpecialBlockHandlers.transformNbt(
                  state,
                  block.nbt(),
                  StructurePlacement.rotationFor(deployment.facing()),
                  level.registryAccess()),
              localClipY,
              false));
    }
  }

  private static void renderDeployOverlay(
      PoseStack poseStack,
      MultiBufferSource.BufferSource bufferSource,
      Level level,
      StructureDeployment deployment,
      float partialTick,
      Frame frame) {
    float deployY = deployment.getDeployLineY(level, partialTick);
    float progress = deployment.getProgress(level, partialTick);
    VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
    renderDeployPlane(
        poseStack,
        lines,
        deployment.boundsMin(),
        deployment.planeSize(),
        deployY,
        progress,
        frame.plotOrigin());
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
      float progress,
      Vec3 plotOrigin) {
    double minX = origin.getX() - plotOrigin.x;
    double minZ = origin.getZ() - plotOrigin.z;
    double maxX = origin.getX() + Math.max(1, size.getX()) - plotOrigin.x;
    double maxZ = origin.getZ() + Math.max(1, size.getZ()) - plotOrigin.z;
    double bottom = origin.getY() - plotOrigin.y;
    double top = origin.getY() + Math.max(1, size.getY()) - plotOrigin.y;
    double planeY = deployY - plotOrigin.y;
    float pulse = 0.7F + 0.3F * Mth.sin(deployY * 8.0F + progress * 20.0F);

    LevelRenderer.renderLineBox(
        poseStack,
        lines,
        minX,
        bottom,
        minZ,
        maxX,
        top,
        maxZ,
        LINE_RED,
        LINE_GREEN,
        LINE_BLUE,
        0.15F);
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
        0.95F * pulse);
  }

  private record BoundsY(double minY, double maxY) {}
}
