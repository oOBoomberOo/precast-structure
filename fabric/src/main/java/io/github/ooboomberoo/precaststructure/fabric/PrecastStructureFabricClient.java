package io.github.ooboomberoo.precaststructure.fabric;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.client.ModShaders;
import io.github.ooboomberoo.precaststructure.client.PrecastStructureClient;
import io.github.ooboomberoo.precaststructure.client.StructureGhostRenderer;
import io.github.ooboomberoo.precaststructure.client.StructureItemRenderer;
import io.github.ooboomberoo.precaststructure.client.StructureScanRenderer;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.resources.ResourceLocation;

public final class PrecastStructureFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PrecastStructureClient.init();
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
            ResourceLocation.fromNamespaceAndPath(PrecastStructureMod.MOD_ID, ModShaders.SCAN_HOLOGRAM),
            DefaultVertexFormat.BLOCK,
            ModShaders::setScanHologram
        ));
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.PRECAST_STRUCTURE.get(), StructureItemRenderer::render);
        // After opaque/cutout world geometry so depth buffer occludes holograms correctly.
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            StructureScanRenderer.render(
                context.matrixStack(),
                context.camera().getPosition(),
                context.tickCounter().getGameTimeDeltaPartialTick(false)
            );
            StructureGhostRenderer.render(context.matrixStack(), context.camera().getPosition());
        });
    }
}
