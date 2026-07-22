package io.github.ooboomberoo.precaststructure.registry;

import net.minecraft.world.level.GameRules;

public final class ModGameRules {
    public static final GameRules.Key<GameRules.IntegerValue> STRUCTURE_PRINTER_DELAY = GameRules.register("precastStructurePrinterDelay", GameRules.Category.UPDATES, GameRules.IntegerValue.create(100));

    private ModGameRules() {
    }

    public static void register() {
    }
}
