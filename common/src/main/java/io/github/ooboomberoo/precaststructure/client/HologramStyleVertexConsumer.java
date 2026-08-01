package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.FastColor;

/**
 * Applies {@link HologramEffectMath} on the CPU so Iris/Oculus shader packs still get the
 * hologram look while using vanilla programs Iris can remap.
 */
public final class HologramStyleVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float cameraX;
    private final float cameraY;
    private final float cameraZ;
    private final float time;
    private float lastX;
    private float lastY;
    private float lastZ;

    public HologramStyleVertexConsumer(VertexConsumer delegate, float cameraX, float cameraY, float cameraZ, float time) {
        this.delegate = delegate;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.time = time;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        lastX = (float) x;
        lastY = (float) y;
        lastZ = (float) z;
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        int styled = style(FastColor.ARGB32.color(alpha, red, green, blue));
        delegate.color(
            FastColor.ARGB32.red(styled),
            FastColor.ARGB32.green(styled),
            FastColor.ARGB32.blue(styled),
            FastColor.ARGB32.alpha(styled)
        );
        return this;
    }

    private int style(int argb) {
        return HologramEffectMath.styleColor(
            argb,
            lastX + cameraX,
            lastY + cameraY,
            lastZ + cameraZ,
            time
        );
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        delegate.defaultColor(red, green, blue, alpha);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}
