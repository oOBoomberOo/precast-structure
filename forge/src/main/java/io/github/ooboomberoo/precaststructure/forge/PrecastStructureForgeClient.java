package io.github.ooboomberoo.precaststructure.forge;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.client.ModShaders;
import io.github.ooboomberoo.precaststructure.client.PrecastStructureClient;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = PrecastStructureMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
                new ResourceLocation(PrecastStructureMod.MOD_ID, ModShaders.SCAN_HOLOGRAM),
                DefaultVertexFormat.BLOCK
            ),
            ModShaders::setScanHologram
        );
    }
}
