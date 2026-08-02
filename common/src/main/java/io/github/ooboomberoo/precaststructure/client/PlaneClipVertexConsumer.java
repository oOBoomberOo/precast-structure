package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Vec3i;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

/**
 * Clips geometry against a horizontal wipe plane using Sutherland–Hodgman.
 *
 * <p><b>putBulkData</b> (block models): quads arrive in model space — clip at
 * {@code modelClipY}, then transform with the pose matrix.
 *
 * <p><b>one-shot addVertex</b> (ModelPart / BER meshes): vanilla transforms first, then
 * calls {@link #addVertex(float, float, float, int, float, float, int, int, float, float, float)}.
 * Those verts are already in pose-output space — clip at {@code outputClipY} and emit
 * without re-transforming. Fluent {@code addVertex(Pose,...)} buffering is intentionally
 * unused: ModelPart never takes that path.
 */
public final class PlaneClipVertexConsumer implements VertexConsumer {
    private static final int MAX_CLIP_VERTS = 8;

    private final VertexConsumer delegate;
    /** Block-model space plane (putBulkData). */
    private final float modelClipY;
    /** Pose-output space plane (one-shot BER verts). */
    private final float outputClipY;
    private final boolean keepBelow;
    private final ClipVert[] input = new ClipVert[4];
    private final ClipVert[] clipA = new ClipVert[MAX_CLIP_VERTS];
    private final ClipVert[] clipB = new ClipVert[MAX_CLIP_VERTS];

    private int oneshotCount;

    public PlaneClipVertexConsumer(VertexConsumer delegate, float modelClipY, boolean keepBelow) {
        this(delegate, modelClipY, modelClipY, keepBelow);
    }

    /**
     * @param modelClipY  wipe Y in block-model space (for baked quads)
     * @param outputClipY wipe Y in the same space as already-transformed BER verts
     *                    (typically {@code blockPose.transformPosition(0, modelClipY, 0).y})
     */
    public PlaneClipVertexConsumer(
        VertexConsumer delegate,
        float modelClipY,
        float outputClipY,
        boolean keepBelow
    ) {
        this.delegate = delegate;
        this.modelClipY = modelClipY;
        this.outputClipY = outputClipY;
        this.keepBelow = keepBelow;
        for (int i = 0; i < 4; i++) {
            input[i] = new ClipVert();
        }
        for (int i = 0; i < MAX_CLIP_VERTS; i++) {
            clipA[i] = new ClipVert();
            clipB[i] = new ClipVert();
        }
    }

    @Override
    public void putBulkData(
        PoseStack.Pose pose,
        BakedQuad quad,
        float red,
        float green,
        float blue,
        float alpha,
        int packedLight,
        int packedOverlay
    ) {
        putBulkData(
            pose,
            quad,
            new float[]{1.0F, 1.0F, 1.0F, 1.0F},
            red,
            green,
            blue,
            alpha,
            new int[]{packedLight, packedLight, packedLight, packedLight},
            packedOverlay,
            false
        );
    }

    @Override
    public void putBulkData(
        PoseStack.Pose pose,
        BakedQuad quad,
        float[] brightness,
        float red,
        float green,
        float blue,
        float alpha,
        int[] lightmap,
        int packedOverlay,
        boolean colorize
    ) {
        int[] vertices = quad.getVertices();
        Vec3i normal = quad.getDirection().getNormal();
        Matrix4f matrix = pose.pose();
        Vector3f transformedNormal = pose.transformNormal(normal.getX(), normal.getY(), normal.getZ(), new Vector3f());
        int alphaByte = (int) (alpha * 255.0F);
        int vertexCount = vertices.length / 8;
        if (vertexCount != 4) {
            VertexConsumer.super.putBulkData(pose, quad, brightness, red, green, blue, alpha, lightmap, packedOverlay, colorize);
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer byteBuffer = stack.malloc(DefaultVertexFormat.BLOCK.getVertexSize());
            IntBuffer intBuffer = byteBuffer.asIntBuffer();
            for (int i = 0; i < 4; i++) {
                intBuffer.clear();
                intBuffer.put(vertices, i * 8, 8);
                float x = byteBuffer.getFloat(0);
                float y = byteBuffer.getFloat(4);
                float z = byteBuffer.getFloat(8);
                float shade = brightness[i];
                int color;
                if (colorize) {
                    float cr = (byteBuffer.get(12) & 0xFF) * shade * red;
                    float cg = (byteBuffer.get(13) & 0xFF) * shade * green;
                    float cb = (byteBuffer.get(14) & 0xFF) * shade * blue;
                    color = FastColor.ARGB32.color(alphaByte, (int) cr, (int) cg, (int) cb);
                } else {
                    color = FastColor.ARGB32.color(
                        alphaByte,
                        (int) (shade * red * 255.0F),
                        (int) (shade * green * 255.0F),
                        (int) (shade * blue * 255.0F)
                    );
                }
                input[i].set(
                    x, y, z, color, byteBuffer.getFloat(16), byteBuffer.getFloat(20),
                    lightmap[i], packedOverlay, transformedNormal.x, transformedNormal.y, transformedNormal.z
                );
            }
        }

        emitClippedQuad(matrix, modelClipY);
    }

    /**
     * ModelPart / BER path: positions are already pose-transformed.
     * SpriteCoordinateExpander forwards this overload to the inner consumer.
     */
    @Override
    public void addVertex(
        float x,
        float y,
        float z,
        int color,
        float u,
        float v,
        int packedOverlay,
        int packedLight,
        float normalX,
        float normalY,
        float normalZ
    ) {
        input[oneshotCount].set(x, y, z, color, u, v, packedLight, packedOverlay, normalX, normalY, normalZ);
        oneshotCount++;
        if (oneshotCount == 4) {
            emitClippedQuad(null, outputClipY);
            oneshotCount = 0;
        }
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(red, green, blue, alpha);
        return this;
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

    /**
     * Emit clipped polygon as {@link VertexFormat.Mode#QUADS} primitives.
     * {@code matrix == null} means verts are already transformed (oneshot BER).
     */
    private void emitClippedQuad(Matrix4f matrix, float clipY) {
        int clippedCount = clipQuad(input, clipY, keepBelow, clipA, clipB);
        if (clippedCount < 3) {
            return;
        }
        if (clippedCount == 4) {
            emitVert(matrix, clipA[0]);
            emitVert(matrix, clipA[1]);
            emitVert(matrix, clipA[2]);
            emitVert(matrix, clipA[3]);
        } else {
            // Triangle fan as degenerate quads (4 verts each) for QUADS buffers.
            ClipVert v0 = clipA[0];
            for (int i = 1; i < clippedCount - 1; i++) {
                emitVert(matrix, v0);
                emitVert(matrix, clipA[i]);
                emitVert(matrix, clipA[i + 1]);
                emitVert(matrix, clipA[i + 1]);
            }
        }
    }

    private void emitVert(Matrix4f matrix, ClipVert vert) {
        if (matrix == null) {
            delegate.addVertex(
                vert.x, vert.y, vert.z, vert.color, vert.u, vert.v, vert.overlay, vert.light,
                vert.nx, vert.ny, vert.nz
            );
            return;
        }
        Vector3f pos = matrix.transformPosition(vert.x, vert.y, vert.z, new Vector3f());
        delegate.addVertex(
            pos.x, pos.y, pos.z, vert.color, vert.u, vert.v, vert.overlay, vert.light,
            vert.nx, vert.ny, vert.nz
        );
    }

    private static int clipQuad(ClipVert[] quad, float clipY, boolean keepBelow, ClipVert[] out, ClipVert[] scratch) {
        for (int i = 0; i < 4; i++) {
            out[i].copyFrom(quad[i]);
        }
        int clipped = clipAgainstPlane(out, 4, scratch, clipY, keepBelow);
        if (clipped < 3) {
            return clipped;
        }
        for (int i = 0; i < clipped; i++) {
            out[i].copyFrom(scratch[i]);
        }
        return clipped;
    }

    private static int clipAgainstPlane(ClipVert[] in, int inCount, ClipVert[] out, float clipY, boolean keepBelow) {
        int outCount = 0;
        ClipVert prev = in[inCount - 1];
        boolean prevInside = isInside(prev.y, clipY, keepBelow);
        for (int i = 0; i < inCount; i++) {
            ClipVert curr = in[i];
            boolean currInside = isInside(curr.y, clipY, keepBelow);
            if (currInside) {
                if (!prevInside) {
                    out[outCount++].lerpToPlane(prev, curr, clipY);
                }
                out[outCount++].copyFrom(curr);
            } else if (prevInside) {
                out[outCount++].lerpToPlane(prev, curr, clipY);
            }
            prev = curr;
            prevInside = currInside;
        }
        return outCount;
    }

    private static boolean isInside(float y, float clipY, boolean keepBelow) {
        return keepBelow ? y <= clipY + HologramRenderSystem.CLIP_EPSILON : y >= clipY - HologramRenderSystem.CLIP_EPSILON;
    }

    private static final class ClipVert {
        float x;
        float y;
        float z;
        int color;
        float u;
        float v;
        int light;
        int overlay;
        float nx;
        float ny;
        float nz;

        void set(
            float x, float y, float z, int color, float u, float v, int light, int overlay,
            float nx, float ny, float nz
        ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
            this.u = u;
            this.v = v;
            this.light = light;
            this.overlay = overlay;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }

        void copyFrom(ClipVert other) {
            set(other.x, other.y, other.z, other.color, other.u, other.v, other.light, other.overlay,
                other.nx, other.ny, other.nz);
        }

        void lerpToPlane(ClipVert from, ClipVert to, float clipY) {
            float dy = to.y - from.y;
            float t = Math.abs(dy) < 1.0E-6F ? 0.0F : (clipY - from.y) / dy;
            t = Mth.clamp(t, 0.0F, 1.0F);
            x = Mth.lerp(t, from.x, to.x);
            y = clipY;
            z = Mth.lerp(t, from.z, to.z);
            u = Mth.lerp(t, from.u, to.u);
            v = Mth.lerp(t, from.v, to.v);
            color = lerpColor(from.color, to.color, t);
            light = lerpLight(from.light, to.light, t);
            overlay = from.overlay;
            nx = Mth.lerp(t, from.nx, to.nx);
            ny = Mth.lerp(t, from.ny, to.ny);
            nz = Mth.lerp(t, from.nz, to.nz);
        }

        private static int lerpColor(int a, int b, float t) {
            return FastColor.ARGB32.color(
                (int) Mth.lerp(t, FastColor.ARGB32.alpha(a), FastColor.ARGB32.alpha(b)),
                (int) Mth.lerp(t, FastColor.ARGB32.red(a), FastColor.ARGB32.red(b)),
                (int) Mth.lerp(t, FastColor.ARGB32.green(a), FastColor.ARGB32.green(b)),
                (int) Mth.lerp(t, FastColor.ARGB32.blue(a), FastColor.ARGB32.blue(b))
            );
        }

        private static int lerpLight(int a, int b, float t) {
            int block = (int) Mth.lerp(t, a & 0xFFFF, b & 0xFFFF);
            int sky = (int) Mth.lerp(t, a >> 16 & 0xFFFF, b >> 16 & 0xFFFF);
            return block | sky << 16;
        }
    }
}
