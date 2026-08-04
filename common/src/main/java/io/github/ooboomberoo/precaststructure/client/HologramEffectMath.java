package io.github.ooboomberoo.precaststructure.client;

import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Shared hologram look used by the core fragment shader and the Iris-safe vertex path. Mirrors
 * {@code scan_hologram.fsh} so shader-pack users still get scanlines / sweeps / glitches.
 */
public final class HologramEffectMath {
  private HologramEffectMath() {}

  public static float shaderTimeSeconds() {
    return com.mojang.blaze3d.systems.RenderSystem.getShaderGameTime() * 1200.0F;
  }

  public static float hash(float x, float y) {
    float v = Mth.sin(x * 127.1F + y * 311.7F) * 43758.5453F;
    return v - Mth.floor(v);
  }

  /**
   * Applies hologram colour grading + animated effects to a packed ARGB vertex colour.
   *
   * @param worldY camera-relative Y plus camera Y (true world Y)
   */
  public static int styleColor(int argb, float worldX, float worldY, float worldZ, float time) {
    float a = FastColor.ARGB32.alpha(argb) / 255.0F;
    float r = FastColor.ARGB32.red(argb) / 255.0F;
    float g = FastColor.ARGB32.green(argb) / 255.0F;
    float b = FastColor.ARGB32.blue(argb) / 255.0F;

    float lum = r * 0.299F + g * 0.587F + b * 0.114F;
    float tr = r * 0.75F;
    float tg = g * 1.05F;
    float tb = b * 1.25F;

    // Cool hologram mix (matches fragment shader intent).
    float hbR = 0.35F;
    float hbG = 0.82F;
    float hbB = 1.0F;
    float hcR = 0.55F;
    float hcG = 0.98F;
    float hcB = 1.0F;
    float mixBlue = 0.38F;
    tr = Mth.lerp(mixBlue, tr, hbR * (0.55F + lum * 0.9F));
    tg = Mth.lerp(mixBlue, tg, hbG * (0.55F + lum * 0.9F));
    tb = Mth.lerp(mixBlue, tb, hbB * (0.55F + lum * 0.9F));
    float mixCyan = 0.18F;
    float cl = Math.max(lum, 0.25F);
    tr = Mth.lerp(mixCyan, tr, hcR * cl);
    tg = Mth.lerp(mixCyan, tg, hcG * cl);
    tb = Mth.lerp(mixCyan, tb, hcB * cl);

    float lift = 1.35F + lum * 0.35F;
    tr = Math.min(tr * lift, 1.6F);
    tg = Math.min(tg * lift, 1.6F);
    tb = Math.min(tb * lift, 1.6F);

    float scan = Mth.sin((worldY - time * 0.85F) * 90.0F);
    float scanMul = 0.90F + 0.14F * scan;
    tr *= scanMul;
    tg *= scanMul;
    tb *= scanMul;

    float sweep = fract(worldY * 0.45F - time * 0.85F);
    float beam = smoothstep(0.0F, 0.04F, sweep) * smoothstep(0.18F, 0.04F, sweep);
    tr += hcR * beam * 0.45F;
    tg += hcG * beam * 0.45F;
    tb += hcB * beam * 0.45F;
    float beamMul = 1.0F + beam * 0.25F;
    tr *= beamMul;
    tg *= beamMul;
    tb *= beamMul;

    float sweep2 = fract(worldY * 0.2F + time * 0.35F);
    float beam2 = smoothstep(0.0F, 0.03F, sweep2) * smoothstep(0.12F, 0.03F, sweep2);
    tr += hbR * beam2 * 0.18F;
    tg += hbG * beam2 * 0.18F;
    tb += hbB * beam2 * 0.18F;

    float tearRow = Mth.floor(worldY * 16.0F + time * 7.0F);
    float tear = hash(tearRow, Mth.floor(time * 4.0F)) > 0.965F ? 1.0F : 0.0F;
    tr = Mth.lerp(tear, tr, tr * 1.15F + hcR * 0.25F);
    tg = Mth.lerp(tear, tg, tg * 1.35F + hcG * 0.25F);
    tb = Mth.lerp(tear, tb, tb * 1.55F + hcB * 0.25F);

    float flicker = 0.92F + 0.08F * Mth.sin(time * 22.0F + worldY * 5.0F);
    flicker *= 0.96F + 0.04F * Mth.sin(time * 61.0F);
    float dropout = hash(Mth.floor(time * 8.0F), 9.7F) > 0.985F ? 0.88F : 1.0F;
    flicker *= dropout;
    tr *= flicker;
    tg *= flicker;
    tb *= flicker;

    float noise =
        hash(Mth.floor(worldX * 24.0F) + Mth.floor(time * 12.0F), Mth.floor(worldZ * 24.0F));
    tr += hcR * noise * 0.06F;
    tg += hcG * noise * 0.06F;
    tb += hcB * noise * 0.06F;

    float alpha = Mth.clamp(a * (0.74F + beam * 0.16F + beam2 * 0.06F) * flicker, 0.0F, 0.94F);
    return FastColor.ARGB32.color(
        (int) (alpha * 255.0F),
        (int) (Mth.clamp(tr, 0.0F, 1.0F) * 255.0F),
        (int) (Mth.clamp(tg, 0.0F, 1.0F) * 255.0F),
        (int) (Mth.clamp(tb, 0.0F, 1.0F) * 255.0F));
  }

  private static float fract(float v) {
    return v - Mth.floor(v);
  }

  private static float smoothstep(float edge0, float edge1, float x) {
    float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
    return t * t * (3.0F - 2.0F * t);
  }
}
