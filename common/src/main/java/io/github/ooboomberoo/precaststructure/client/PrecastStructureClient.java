package io.github.ooboomberoo.precaststructure.client;

import dev.architectury.registry.menu.MenuRegistry;
import io.github.ooboomberoo.precaststructure.client.screen.StructurePrinterScreen;
import io.github.ooboomberoo.precaststructure.client.screen.StructureScannerScreen;
import io.github.ooboomberoo.precaststructure.registry.ModMenuTypes;

public final class PrecastStructureClient {
    private PrecastStructureClient() {
    }

    public static void init() {
        MenuRegistry.registerScreenFactory(ModMenuTypes.STRUCTURE_SCANNER.get(), StructureScannerScreen::new);
        MenuRegistry.registerScreenFactory(ModMenuTypes.STRUCTURE_PRINTER.get(), StructurePrinterScreen::new);
    }
}
