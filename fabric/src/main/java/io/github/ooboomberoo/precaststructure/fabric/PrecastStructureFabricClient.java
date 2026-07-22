package io.github.ooboomberoo.precaststructure.fabric;

import io.github.ooboomberoo.precaststructure.client.StructureGhostRenderer;
import io.github.ooboomberoo.precaststructure.client.StructureItemRenderer;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;

public final class PrecastStructureFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.PRECAST_STRUCTURE.get(), StructureItemRenderer::render);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> StructureGhostRenderer.render(context.matrixStack(), context.camera().getPosition(), Minecraft.getInstance().renderBuffers().bufferSource()));
    }
}
