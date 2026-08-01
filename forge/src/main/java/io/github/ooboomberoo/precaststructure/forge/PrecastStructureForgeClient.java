package io.github.ooboomberoo.precaststructure.forge;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.client.ModShaders;
import io.github.ooboomberoo.precaststructure.client.PrecastStructureClient;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

@EventBusSubscriber(modid = PrecastStructureMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PrecastStructureForgeClient {
    private PrecastStructureForgeClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(PrecastStructureClient::init);
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws Exception {
        event.registerShader(
            new ShaderInstance(
                event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(PrecastStructureMod.MOD_ID, ModShaders.SCAN_HOLOGRAM),
                DefaultVertexFormat.BLOCK
            ),
            ModShaders::setScanHologram
        );
    }
}
