package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Shared hologram mesh renderer (depth prepass + translucent color) used by scan ghosts
 * and structure placement preview.
 */
public final class StructureHologramRenderer {
    public static final float CLIP_EPSILON = 0.001F;

    private StructureHologramRenderer() {
    }

    public record Part(BlockPos worldPos, BlockState state, @Nullable Float clipY, boolean keepBelow) {
        public static Part of(BlockPos worldPos, BlockState state) {
            return new Part(worldPos, state, null, false);
        }
    }

    /**
     * Renders hologram parts with depth prepass + color pass into a fresh buffer.
     */
    public static void render(PoseStack poseStack, Vec3 cameraPosition, Iterable<Part> parts, float colorR, float colorG, float colorB) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(768 * 1024)) {
            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(byteBuffer);

        RenderSystem.enableDepthTest();
        // Iris path already cyans in HologramStyleVertexConsumer; ColorModulator still handles blocked red.
        float alpha = ModShaders.useCustomHologramShader() ? 1.0F : 0.9F;
        RenderSystem.setShaderColor(colorR, colorG, colorB, alpha);

        if (ModRenderTypes.useHologramDepthPrepass()) {
            RenderSystem.depthMask(true);
            renderPass(poseStack, cameraPosition, bufferSource, dispatcher, parts, true);
            bufferSource.endBatch();

            RenderSystem.depthMask(false);
            renderPass(poseStack, cameraPosition, bufferSource, dispatcher, parts, false);
            bufferSource.endBatch();
        } else {
            // Single translucent pass: Iris remaps solid depth-only draws into gbuffers.
            RenderSystem.depthMask(true);
            renderPass(poseStack, cameraPosition, bufferSource, dispatcher, parts, false);
            bufferSource.endBatch();
        }

        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
    }

    /**
     * Emits hologram meshes into an existing buffer (one depth or color pass).
     */
    public static void renderPass(
        PoseStack poseStack,
        Vec3 cameraPosition,
        MultiBufferSource.BufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        Iterable<Part> parts,
        boolean depthPass
    ) {
        if (depthPass && !ModRenderTypes.useHologramDepthPrepass()) {
            return;
        }
        RenderType hologramType = depthPass ? ModRenderTypes.scanHologramDepth() : ModRenderTypes.scanHologram();
        boolean styleEffects = !depthPass && !ModShaders.useCustomHologramShader();
        float time = HologramEffectMath.shaderTimeSeconds();
        float camX = (float) cameraPosition.x;
        float camY = (float) cameraPosition.y;
        float camZ = (float) cameraPosition.z;

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

        for (Part part : parts) {
            poseStack.pushPose();
            poseStack.translate(part.worldPos().getX(), part.worldPos().getY(), part.worldPos().getZ());
            renderPart(poseStack, bufferSource, dispatcher, part, hologramType, styleEffects, camX, camY, camZ, time);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static void renderPart(
        PoseStack poseStack,
        MultiBufferSource.BufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        Part part,
        RenderType hologramType,
        boolean styleEffects,
        float cameraX,
        float cameraY,
        float cameraZ,
        float time
    ) {
        MultiBufferSource source = renderType -> {
            VertexConsumer buffer = bufferSource.getBuffer(hologramType);
            if (styleEffects) {
                buffer = new HologramStyleVertexConsumer(buffer, cameraX, cameraY, cameraZ, time);
            }
            if (part.clipY() != null) {
                buffer = new PlaneClipVertexConsumer(buffer, part.clipY(), part.keepBelow());
            }
            return buffer;
        };
        dispatcher.renderSingleBlock(part.state(), poseStack, source, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }

    /**
     * Clips baked quads against a horizontal model-space plane (y = localClipY)
     * using Sutherland–Hodgman polygon clipping.
     */
    static final class PlaneClipVertexConsumer implements VertexConsumer {
        private static final int MAX_CLIP_VERTS = 8;

        private final VertexConsumer delegate;
        private final float localClipY;
        private final boolean keepBelow;
        private final ClipVert[] input = new ClipVert[4];
        private final ClipVert[] clipA = new ClipVert[MAX_CLIP_VERTS];
        private final ClipVert[] clipB = new ClipVert[MAX_CLIP_VERTS];

        PlaneClipVertexConsumer(VertexConsumer delegate, float localClipY, boolean keepBelow) {
            this.delegate = delegate;
            this.localClipY = localClipY;
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
                    input[i].set(x, y, z, color, byteBuffer.getFloat(16), byteBuffer.getFloat(20), lightmap[i]);
                }
            }

            int clippedCount = clipQuad(input, localClipY, keepBelow, clipA, clipB);
            if (clippedCount < 3) {
                return;
            }

            ClipVert v0 = clipA[0];
            for (int i = 1; i < clippedCount - 1; i++) {
                emitTransformed(matrix, v0, packedOverlay, transformedNormal);
                emitTransformed(matrix, clipA[i], packedOverlay, transformedNormal);
                emitTransformed(matrix, clipA[i + 1], packedOverlay, transformedNormal);
                emitTransformed(matrix, clipA[i + 1], packedOverlay, transformedNormal);
            }
        }

        private void emitTransformed(Matrix4f matrix, ClipVert vert, int packedOverlay, Vector3f normal) {
            Vector3f pos = matrix.transformPosition(vert.x, vert.y, vert.z, new Vector3f());
            delegate.addVertex(
                pos.x,
                pos.y,
                pos.z,
                vert.color,
                vert.u,
                vert.v,
                packedOverlay,
                vert.light,
                normal.x,
                normal.y,
                normal.z
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
            return keepBelow ? y <= clipY + CLIP_EPSILON : y >= clipY - CLIP_EPSILON;
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

        private static final class ClipVert {
            float x;
            float y;
            float z;
            int color;
            float u;
            float v;
            int light;

            void set(float x, float y, float z, int color, float u, float v, int light) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.color = color;
                this.u = u;
                this.v = v;
                this.light = light;
            }

            void copyFrom(ClipVert other) {
                set(other.x, other.y, other.z, other.color, other.u, other.v, other.light);
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
}
