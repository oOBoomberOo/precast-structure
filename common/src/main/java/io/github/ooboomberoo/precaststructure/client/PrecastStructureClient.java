package io.github.ooboomberoo.precaststructure.client;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.platform.Platform;
import dev.architectury.registry.client.rendering.RenderTypeRegistry;
import dev.architectury.registry.menu.MenuRegistry;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.client.screen.StructurePrinterScreen;
import io.github.ooboomberoo.precaststructure.client.screen.StructureScannerScreen;
import io.github.ooboomberoo.precaststructure.config.ModConfigScreen;
import io.github.ooboomberoo.precaststructure.network.ModNetworking;
import io.github.ooboomberoo.precaststructure.registry.ModBlocks;
import io.github.ooboomberoo.precaststructure.registry.ModMenuTypes;
import io.github.ooboomberoo.precaststructure.structure.StructureDeploymentManager;
import net.minecraft.client.renderer.RenderType;

public final class PrecastStructureClient {
    private PrecastStructureClient() {
    }

    public static void init() {
        ModNetworking.registerClient();
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> StructureDeploymentManager.clientClear());
        ClientTickEvent.CLIENT_POST.register(minecraft -> StructureDeploymentManager.clientTick(minecraft.level));
        // NeoForge requires RegisterMenuScreensEvent; Architectury's helper is a no-op there.
        if (!Platform.isNeoForge()) {
            MenuRegistry.registerScreenFactory(ModMenuTypes.STRUCTURE_SCANNER.get(), StructureScannerScreen::new);
            MenuRegistry.registerScreenFactory(ModMenuTypes.STRUCTURE_PRINTER.get(), StructurePrinterScreen::new);
        }
        RenderTypeRegistry.register(RenderType.cutout(), ModBlocks.METAL_SCAFFOLD.get());
        registerConfigScreen();
    }

    private static void registerConfigScreen() {
        if (Platform.isModLoaded("cloth-config") || Platform.isModLoaded("cloth_config")) {
            Platform.getMod(PrecastStructureMod.MOD_ID).registerConfigurationScreen(ModConfigScreen::create);
        }
    }
}
