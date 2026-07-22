package io.github.ooboomberoo.precaststructure;

import io.github.ooboomberoo.precaststructure.network.ModNetworking;
import io.github.ooboomberoo.precaststructure.registry.ModBlockEntityTypes;
import io.github.ooboomberoo.precaststructure.registry.ModBlocks;
import io.github.ooboomberoo.precaststructure.registry.ModGameRules;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import io.github.ooboomberoo.precaststructure.registry.ModMenuTypes;

public final class PrecastStructureMod {
    public static final String MOD_ID = "precaststructure";

    private PrecastStructureMod() {
    }

    public static void init() {
        ModGameRules.register();
        ModBlocks.register();
        ModItems.register();
        ModBlockEntityTypes.register();
        ModMenuTypes.register();
        ModNetworking.register();
    }
}
