package io.github.ooboomberoo.precaststructure.fabric;

import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import net.fabricmc.api.ModInitializer;

public final class PrecastStructureFabric implements ModInitializer {
  @Override
  public void onInitialize() {
    PrecastStructureMod.init();
  }
}
