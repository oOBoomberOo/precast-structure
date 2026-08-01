package io.github.ooboomberoo.precaststructure.client;

import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;

public final class ModShaders {
    public static final String SCAN_HOLOGRAM = "scan_hologram";

    @Nullable
    private static ShaderInstance scanHologram;

    private ModShaders() {
    }

    public static void setScanHologram(@Nullable ShaderInstance shader) {
        scanHologram = shader;
    }

    @Nullable
    public static ShaderInstance getScanHologram() {
        return scanHologram;
    }
}
