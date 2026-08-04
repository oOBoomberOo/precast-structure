package io.github.ooboomberoo.precaststructure.client;

import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;

public final class ModShaders {
  public static final String SCAN_HOLOGRAM = "scan_hologram";
  public static final String SCAN_HOLOGRAM_ENTITY = "scan_hologram_entity";

  @Nullable private static ShaderInstance scanHologram;
  @Nullable private static ShaderInstance scanHologramEntity;

  private ModShaders() {}

  public static void setScanHologram(@Nullable ShaderInstance shader) {
    scanHologram = shader;
  }

  public static void setScanHologramEntity(@Nullable ShaderInstance shader) {
    scanHologramEntity = shader;
  }

  @Nullable
  public static ShaderInstance getScanHologram() {
    return scanHologram;
  }

  @Nullable
  public static ShaderInstance getScanHologramEntity() {
    return scanHologramEntity;
  }

  /**
   * Custom hologram core shader is only used when Iris/Oculus does not have a shader pack loaded.
   * With a pack active, Iris drops custom programs; holograms instead use vanilla solid/translucent
   * programs plus {@link HologramStyleVertexConsumer} for the same look.
   */
  public static boolean useCustomHologramShader() {
    return scanHologram != null && !ShaderCompat.isExternalShaderPackActive();
  }

  /** Entity-format BER hologram program (chest / shulker / skull). */
  public static boolean useCustomEntityHologramShader() {
    return scanHologramEntity != null && !ShaderCompat.isExternalShaderPackActive();
  }
}
