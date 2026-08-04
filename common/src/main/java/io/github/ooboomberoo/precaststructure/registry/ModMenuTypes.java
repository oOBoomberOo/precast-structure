package io.github.ooboomberoo.precaststructure.registry;

import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.menu.StructurePrinterMenu;
import io.github.ooboomberoo.precaststructure.menu.StructureScannerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

public final class ModMenuTypes {
  public static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(PrecastStructureMod.MOD_ID, Registries.MENU);

  public static final RegistrySupplier<MenuType<StructureScannerMenu>> STRUCTURE_SCANNER =
      MENUS.register("structure_scanner", () -> MenuRegistry.ofExtended(StructureScannerMenu::new));
  public static final RegistrySupplier<MenuType<StructurePrinterMenu>> STRUCTURE_PRINTER =
      MENUS.register("structure_printer", () -> MenuRegistry.ofExtended(StructurePrinterMenu::new));

  private ModMenuTypes() {}

  public static void register() {
    MENUS.register();
  }
}
