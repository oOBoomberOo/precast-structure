package io.github.ooboomberoo.precaststructure;

import io.github.ooboomberoo.precaststructure.network.ModNetworking;
import io.github.ooboomberoo.precaststructure.registry.ModBlockEntityTypes;
import io.github.ooboomberoo.precaststructure.registry.ModBlocks;
import io.github.ooboomberoo.precaststructure.registry.ModCreativeTabs;
import io.github.ooboomberoo.precaststructure.registry.ModGameRules;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import io.github.ooboomberoo.precaststructure.registry.ModMenuTypes;
import io.github.ooboomberoo.precaststructure.registry.ModRecipeSerializers;

public final class PrecastStructureMod {
    public static final String MOD_ID = "precast_structure";

    private PrecastStructureMod() {
    }

    public static void init() {
        ModGameRules.register();
        ModCreativeTabs.register();
        ModBlocks.register();
        ModItems.register();
        ModBlockEntityTypes.register();
        ModMenuTypes.register();
        ModRecipeSerializers.register();
        ModNetworking.register();
    }
}
