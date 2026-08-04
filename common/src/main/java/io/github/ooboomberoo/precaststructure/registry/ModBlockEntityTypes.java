package io.github.ooboomberoo.precaststructure.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.block.entity.StructurePrinterBlockEntity;
import io.github.ooboomberoo.precaststructure.block.entity.StructureScannerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntityTypes {
  public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
      DeferredRegister.create(PrecastStructureMod.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

  public static final RegistrySupplier<BlockEntityType<StructureScannerBlockEntity>>
      STRUCTURE_SCANNER =
          BLOCK_ENTITY_TYPES.register(
              "structure_scanner",
              () ->
                  BlockEntityType.Builder.of(
                          StructureScannerBlockEntity::new, ModBlocks.STRUCTURE_SCANNER.get())
                      .build(null));
  public static final RegistrySupplier<BlockEntityType<StructurePrinterBlockEntity>>
      STRUCTURE_PRINTER =
          BLOCK_ENTITY_TYPES.register(
              "structure_printer",
              () ->
                  BlockEntityType.Builder.of(
                          StructurePrinterBlockEntity::new, ModBlocks.STRUCTURE_PRINTER.get())
                      .build(null));

  private ModBlockEntityTypes() {}

  public static void register() {
    BLOCK_ENTITY_TYPES.register();
  }
}
