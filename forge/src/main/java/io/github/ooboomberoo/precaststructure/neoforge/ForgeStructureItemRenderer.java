package io.github.ooboomberoo.precaststructure.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.ooboomberoo.precaststructure.client.StructureItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ForgeStructureItemRenderer extends BlockEntityWithoutLevelRenderer {
  public ForgeStructureItemRenderer() {
    super(
        Minecraft.getInstance().getBlockEntityRenderDispatcher(),
        Minecraft.getInstance().getEntityModels());
  }

  @Override
  public void renderByItem(
      ItemStack stack,
      ItemDisplayContext displayContext,
      PoseStack poseStack,
      MultiBufferSource bufferSource,
      int light,
      int overlay) {
    StructureItemRenderer.render(stack, displayContext, poseStack, bufferSource, light, overlay);
  }
}
