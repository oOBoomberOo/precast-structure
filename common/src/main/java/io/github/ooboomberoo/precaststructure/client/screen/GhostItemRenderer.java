package io.github.ooboomberoo.precaststructure.client.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * GUI ghost items: vanilla {@link GuiGraphics#setColor} / {@code setShaderColor} do not tint
 * {@link GuiGraphics#renderItem} (item draws ignore shader color). This path multiplies vertex
 * alpha and forces translucent entity layers so the icon actually blends.
 */
final class GhostItemRenderer {
    private GhostItemRenderer() {
    }

    /** Called reflectively from {@link StructurePrinterScreen} so this class (and ItemRenderer) stay unloaded until paint. */
    public static void render(GuiGraphics guiGraphics, ItemStack stack, int x, int y, float alpha) {
        if (stack.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        BakedModel model = itemRenderer.getModel(stack, null, null, 0);
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x + 8.0F, y + 8.0F, model.isGui3d() ? 150.0F : 150.0F);
        pose.scale(16.0F, -16.0F, 16.0F);

        boolean flat = !model.usesBlockLight();
        if (flat) {
            Lighting.setupForFlatItems();
        }

        MultiBufferSource.BufferSource bufferSource = guiGraphics.bufferSource();
        MultiBufferSource ghostBuffers = renderType -> new AlphaTintVertexConsumer(
            bufferSource.getBuffer(translucentLayer(renderType)),
            alpha
        );
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        itemRenderer.render(
            stack,
            ItemDisplayContext.GUI,
            false,
            pose,
            ghostBuffers,
            0xF000F0,
            OverlayTexture.NO_OVERLAY,
            model
        );
        guiGraphics.flush();

        if (flat) {
            Lighting.setupFor3DItems();
        }
        pose.popPose();
    }

    private static RenderType translucentLayer(RenderType requested) {
        ResourceLocation atlas = atlasFrom(requested);
        if (atlas == null) {
            atlas = ResourceLocation.withDefaultNamespace("textures/atlas/blocks.png");
        }
        return RenderType.entityTranslucentCull(atlas);
    }

    private static @Nullable ResourceLocation atlasFrom(RenderType type) {
        String text = type.toString();
        int optional = text.indexOf("Optional[");
        if (optional < 0) {
            return null;
        }
        int start = optional + "Optional[".length();
        int end = text.indexOf(']', start);
        if (end <= start) {
            return null;
        }
        try {
            return ResourceLocation.parse(text.substring(start, end));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static final class AlphaTintVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float alpha;

        private AlphaTintVertexConsumer(VertexConsumer delegate, float alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public void addVertex(
            float x,
            float y,
            float z,
            int color,
            float u,
            float v,
            int packedOverlay,
            int packedLight,
            float normalX,
            float normalY,
            float normalZ
        ) {
            delegate.addVertex(
                x, y, z, tint(color), u, v, packedOverlay, packedLight, normalX, normalY, normalZ
            );
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alphaByte) {
            int a = Math.max(0, Math.min(255, Math.round(alphaByte * this.alpha)));
            delegate.setColor(red, green, blue, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        private int tint(int argb) {
            int a = Math.max(0, Math.min(255, Math.round(FastColor.ARGB32.alpha(argb) * alpha)));
            return FastColor.ARGB32.color(
                a,
                FastColor.ARGB32.red(argb),
                FastColor.ARGB32.green(argb),
                FastColor.ARGB32.blue(argb)
            );
        }
    }
}
