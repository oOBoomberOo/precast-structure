package io.github.ooboomberoo.precaststructure.forge;

import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.client.PrecastStructureClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = PrecastStructureMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PrecastStructureForgeClient {
    private PrecastStructureForgeClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(PrecastStructureClient::init);
    }
}
