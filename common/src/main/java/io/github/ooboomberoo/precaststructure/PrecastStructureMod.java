package io.github.ooboomberoo.precaststructure;

import io.github.ooboomberoo.precaststructure.compat.CreateCompat;
import io.github.ooboomberoo.precaststructure.config.ModConfig;
import io.github.ooboomberoo.precaststructure.network.ModNetworking;
import io.github.ooboomberoo.precaststructure.registry.ModBlockEntityTypes;
import io.github.ooboomberoo.precaststructure.registry.ModBlocks;
import io.github.ooboomberoo.precaststructure.registry.ModCreativeTabs;
import io.github.ooboomberoo.precaststructure.registry.ModDataComponents;
import io.github.ooboomberoo.precaststructure.registry.ModGameRules;
import io.github.ooboomberoo.precaststructure.registry.ModItems;
import io.github.ooboomberoo.precaststructure.registry.ModMenuTypes;
import io.github.ooboomberoo.precaststructure.registry.ModRecipeSerializers;
import io.github.ooboomberoo.precaststructure.registry.ModSounds;
import io.github.ooboomberoo.precaststructure.structure.BlueprintDataMigration;
import io.github.ooboomberoo.precaststructure.structure.StructureDeploymentManager;
import io.github.ooboomberoo.precaststructure.structure.special.SpecialBlockHandlers;

public final class PrecastStructureMod {
  public static final String MOD_ID = "precast_structure";

  private PrecastStructureMod() {}

  public static void init() {
    ModConfig.load();
    ModGameRules.register();
    ModSounds.register();
    ModCreativeTabs.register();
    ModDataComponents.register();
    ModBlocks.register();
    ModItems.register();
    ModBlockEntityTypes.register();
    ModMenuTypes.register();
    ModRecipeSerializers.register();
    ModNetworking.register();
    StructureDeploymentManager.init();
    BlueprintDataMigration.init();
    SpecialBlockHandlers.bootstrap();
    CreateCompat.registerSpecialHandlers();
  }
}
