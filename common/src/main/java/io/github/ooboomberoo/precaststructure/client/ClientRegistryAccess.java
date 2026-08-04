package io.github.ooboomberoo.precaststructure.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ClientRegistryAccess {
  private ClientRegistryAccess() {}

  public static HolderLookup.Provider getLookup() {
    if (Minecraft.getInstance().level != null) {
      return Minecraft.getInstance().level.registryAccess();
    }
    return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
  }
}
