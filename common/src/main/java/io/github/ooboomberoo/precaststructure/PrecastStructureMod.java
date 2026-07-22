package io.github.ooboomberoo.precaststructure;

import io.github.ooboomberoo.precaststructure.registry.ModBlocks;
import io.github.ooboomberoo.precaststructure.registry.ModItems;

public final class PrecastStructureMod {
    public static final String MOD_ID = "precaststructure";

    private PrecastStructureMod() {
    }

    public static void init() {
        ModBlocks.register();
        ModItems.register();
    }
}
