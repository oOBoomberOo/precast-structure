package io.github.ooboomberoo.precaststructure.forge;

import dev.architectury.platform.forge.EventBuses;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;

@Mod(PrecastStructureMod.MOD_ID)
public final class PrecastStructureForge {
    public PrecastStructureForge(IEventBus modEventBus) {
        EventBuses.registerModEventBus(PrecastStructureMod.MOD_ID, modEventBus);
        PrecastStructureMod.init();
    }
}
