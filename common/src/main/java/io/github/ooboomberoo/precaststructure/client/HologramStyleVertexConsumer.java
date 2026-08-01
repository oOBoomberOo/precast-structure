package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

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
    public VertexConsumer addVertex(float x, float y, float z) {
        lastX = x;
        lastY = y;
        lastZ = z;
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int packedOverlay, int packedLight, float normalX, float normalY, float normalZ) {
        lastX = x;
        lastY = y;
        lastZ = z;
        delegate.addVertex(x, y, z, style(color), u, v, packedOverlay, packedLight, normalX, normalY, normalZ);
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        int styled = style(net.minecraft.util.FastColor.ARGB32.color(alpha, red, green, blue));
        delegate.setColor(
            net.minecraft.util.FastColor.ARGB32.red(styled),
            net.minecraft.util.FastColor.ARGB32.green(styled),
            net.minecraft.util.FastColor.ARGB32.blue(styled),
            net.minecraft.util.FastColor.ARGB32.alpha(styled)
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
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }
}
