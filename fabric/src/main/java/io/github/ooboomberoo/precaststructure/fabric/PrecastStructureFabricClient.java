package io.github.ooboomberoo.precaststructure.fabric;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.client.ModShaders;
import io.github.ooboomberoo.precaststructure.client.PrecastStructureClient;
import io.github.ooboomberoo.precaststructure.client.ShaderCompat;
import io.github.ooboomberoo.precaststructure.client.StructureItemRenderer;
import io.github.ooboomberoo.precaststructure.client.WorldHologramRender;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.resources.ResourceLocation;

public final class PrecastStructureFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PrecastStructureClient.init();
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
            new ResourceLocation(PrecastStructureMod.MOD_ID, ModShaders.SCAN_HOLOGRAM),
            DefaultVertexFormat.BLOCK,
            ModShaders::setScanHologram
        ));
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.PRECAST_STRUCTURE.get(), StructureItemRenderer::render);
        // Vanilla: after opaque/cutout so depth occludes holograms.
        // Iris deferred packs: after translucent so composite does not wipe the draw.
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!ShaderCompat.shouldUseLateWorldOverlayPass()) {
                renderOverlays(context);
            }
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (ShaderCompat.shouldUseLateWorldOverlayPass()) {
                renderOverlays(context);
            }
        });
    }

    private static void renderOverlays(WorldRenderContext context) {
        float partialTick = context.tickDelta();
        WorldHologramRender.renderAll(
            context.matrixStack(),
            context.camera().getPosition(),
            partialTick
        );
    }
}
