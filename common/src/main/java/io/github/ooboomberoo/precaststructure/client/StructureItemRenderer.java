package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.ooboomberoo.precaststructure.structure.BlueprintItemData;
import io.github.ooboomberoo.precaststructure.structure.StructureBlockInfo;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Renders an opaque 3D preview of a precast structure item in hand, inventory, and GUI contexts.
 * The preview is drawn as a solid mesh rather than a translucent hologram.
 */
public final class StructureItemRenderer {
  /** Matches vanilla block item GUI scale for a 1×1×1 cube. */
  private static final float GUI_FIT = 0.625F;

  private static final float HAND_FIT = 0.85F;
  private static final float THIRD_PERSON_FIT = 0.55F;
  private static final float GROUND_FIT = 0.45F;
  private static final float GUI_PITCH = 30.0F;
  private static final float GUI_YAW = 225.0F;

  private StructureItemRenderer() {}

  public static void render(
      ItemStack stack,
      ItemDisplayContext displayContext,
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      int light,
      int overlay) {
    BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
    Optional<StructureBlueprint> optional =
        BlueprintItemData.read(stack, ClientRegistryAccess.getLookup());
    if (optional.isEmpty()) {
      dispatcher.renderSingleBlock(
          Blocks.STRUCTURE_BLOCK.defaultBlockState(),
          poseStack,
          bufferSource,
          LightTexture.FULL_BRIGHT,
          overlay);
      return;
    }

    StructureBlueprint blueprint = optional.get();
    ContentBox box = ContentBox.of(blueprint);

    poseStack.pushPose();
    applyDisplayTransform(poseStack, displayContext, box);
    for (StructureBlockInfo block : blueprint.blocks()) {
      poseStack.pushPose();
      poseStack.translate(block.offset().getX(), block.offset().getY(), block.offset().getZ());
      HologramRenderSystem.renderSolid(
          poseStack, bufferSource, dispatcher, block.state(), block.nbt(), null);
      poseStack.popPose();
    }
    poseStack.popPose();
  }

  private static void applyDisplayTransform(
      PoseStack poseStack, ItemDisplayContext context, ContentBox box) {
    float maxDim = Math.max(1.0F, box.maxDimension());

    switch (context) {
      case GUI, FIXED -> {
        float scale = GUI_FIT / Math.max(1.0F, box.guiProjectedExtent());
        poseStack.translate(0.5F, 0.5F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(GUI_PITCH));
        poseStack.mulPose(Axis.YP.rotationDegrees(GUI_YAW));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-box.centerX(), -box.centerY(), -box.centerZ());
      }
      case GROUND -> {
        float scale = GROUND_FIT / maxDim;
        poseStack.translate(0.5F, 0.05F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-box.centerX(), -box.minY(), -box.centerZ());
      }
      case FIRST_PERSON_LEFT_HAND -> applyFirstPerson(poseStack, box, maxDim, true);
      case FIRST_PERSON_RIGHT_HAND -> applyFirstPerson(poseStack, box, maxDim, false);
      case THIRD_PERSON_LEFT_HAND -> applyThirdPerson(poseStack, box, maxDim, true);
      case THIRD_PERSON_RIGHT_HAND -> applyThirdPerson(poseStack, box, maxDim, false);
      default -> {
        float scale = THIRD_PERSON_FIT / maxDim;
        poseStack.translate(0.5F, 0.35F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-box.centerX(), -box.minY(), -box.centerZ());
      }
    }
  }

  private static void applyFirstPerson(
      PoseStack poseStack, ContentBox box, float maxDim, boolean left) {
    float scale = HAND_FIT / maxDim;
    // Push into view and angle like a held block, resting on the palm.
    poseStack.translate(left ? 0.15F : 0.85F, 0.2F, -0.1F);
    poseStack.mulPose(Axis.YP.rotationDegrees(left ? 75.0F : -75.0F));
    poseStack.mulPose(Axis.XP.rotationDegrees(12.0F));
    poseStack.mulPose(Axis.ZP.rotationDegrees(left ? 8.0F : -8.0F));
    poseStack.scale(scale, scale, scale);
    poseStack.translate(-box.centerX(), -box.minY(), -box.centerZ());
  }

  private static void applyThirdPerson(
      PoseStack poseStack, ContentBox box, float maxDim, boolean left) {
    float scale = THIRD_PERSON_FIT / maxDim;
    poseStack.translate(0.5F, 0.3F, 0.5F);
    poseStack.mulPose(Axis.YP.rotationDegrees(left ? 60.0F : -60.0F));
    poseStack.mulPose(Axis.XP.rotationDegrees(20.0F));
    poseStack.scale(scale, scale, scale);
    poseStack.translate(-box.centerX(), -box.minY(), -box.centerZ());
  }

  private record ContentBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    static ContentBox of(StructureBlueprint blueprint) {
      if (blueprint.blocks().isEmpty()) {
        BlockPos size = blueprint.size();
        return new ContentBox(
            0,
            0,
            0,
            Math.max(0, size.getX() - 1),
            Math.max(0, size.getY() - 1),
            Math.max(0, size.getZ() - 1));
      }

      int minX = Integer.MAX_VALUE;
      int minY = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxY = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;
      for (StructureBlockInfo block : blueprint.blocks()) {
        BlockPos offset = block.offset();
        minX = Math.min(minX, offset.getX());
        minY = Math.min(minY, offset.getY());
        minZ = Math.min(minZ, offset.getZ());
        maxX = Math.max(maxX, offset.getX());
        maxY = Math.max(maxY, offset.getY());
        maxZ = Math.max(maxZ, offset.getZ());
      }
      return new ContentBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    float centerX() {
      return (minX + maxX + 1) * 0.5F;
    }

    float centerY() {
      return (minY + maxY + 1) * 0.5F;
    }

    float centerZ() {
      return (minZ + maxZ + 1) * 0.5F;
    }

    float maxDimension() {
      return Math.max(maxX - minX + 1, Math.max(maxY - minY + 1, maxZ - minZ + 1));
    }

    /**
     * Screen-space extent after the GUI pitch/yaw, so 1×1×1 cubes (and long flats) stay inside the
     * slot.
     */
    float guiProjectedExtent() {
      float cx = centerX();
      float cy = centerY();
      float cz = centerZ();
      float pitch = (float) Math.toRadians(GUI_PITCH);
      float yaw = (float) Math.toRadians(GUI_YAW);
      float cosP = (float) Math.cos(pitch);
      float sinP = (float) Math.sin(pitch);
      float cosY = (float) Math.cos(yaw);
      float sinY = (float) Math.sin(yaw);

      float minSx = Float.POSITIVE_INFINITY;
      float maxSx = Float.NEGATIVE_INFINITY;
      float minSy = Float.POSITIVE_INFINITY;
      float maxSy = Float.NEGATIVE_INFINITY;
      for (int x : new int[] {minX, maxX + 1}) {
        for (int y : new int[] {minY, maxY + 1}) {
          for (int z : new int[] {minZ, maxZ + 1}) {
            float lx = x - cx;
            float ly = y - cy;
            float lz = z - cz;
            // Yaw around Y, then pitch around X (same order as PoseStack mulPose calls).
            float yx = lx * cosY + lz * sinY;
            float yy = ly;
            float yz = -lx * sinY + lz * cosY;
            float px = yx;
            float py = yy * cosP - yz * sinP;
            minSx = Math.min(minSx, px);
            maxSx = Math.max(maxSx, px);
            minSy = Math.min(minSy, py);
            maxSy = Math.max(maxSy, py);
          }
        }
      }
      return Math.max(maxSx - minSx, maxSy - minSy);
    }
  }
}
