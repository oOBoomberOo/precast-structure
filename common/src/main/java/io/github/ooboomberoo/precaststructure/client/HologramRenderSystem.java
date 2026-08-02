package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ooboomberoo.precaststructure.compat.SableCompatClient;
import io.github.ooboomberoo.precaststructure.structure.special.SpecialBlockHandler;
import io.github.ooboomberoo.precaststructure.structure.special.SpecialBlockHandlers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Central hologram pipeline for every mod overlay: scan, placement ghost, deploy,
 * item previews, etc. Owns pose setup (including Sable sub-level transforms), depth
 * prepass + color pass, {@code scan_hologram} / Iris fallback layers, neighbor-face
 * culling, and optional plane clipping.
 *
 * <p>Leaf renderers only supply parts, an optional pose anchor, tint, and clip data.
 */
public final class HologramRenderSystem {
    public static final float CLIP_EPSILON = 0.001F;

    private HologramRenderSystem() {
    }

    public record Part(BlockPos worldPos, BlockState state, @Nullable CompoundTag nbt, @Nullable Float clipY, boolean keepBelow) {
        public static Part of(BlockPos worldPos, BlockState state) {
            return new Part(worldPos, state, null, null, false);
        }

        public static Part of(BlockPos worldPos, BlockState state, @Nullable CompoundTag nbt) {
            return new Part(worldPos, state, nbt, null, false);
        }

        public static Part clipped(BlockPos worldPos, BlockState state, @Nullable CompoundTag nbt, float clipY, boolean keepBelow) {
            return new Part(worldPos, state, nbt, clipY, keepBelow);
        }
    }

    /**
     * Pose framing for one hologram batch. {@link #plotOrigin()} is subtracted from part
     * positions in double precision before they enter the float PoseStack (required for
     * Sable plot-storage coordinates).
     */
    public record Frame(Vec3 plotOrigin) {
        public static Frame world() {
            return new Frame(Vec3.ZERO);
        }
    }

    /**
     * Self-contained world hologram draw into a fresh buffer (placement ghost path).
     * When {@code poseAnchor} sits in a Sable sub-level, applies the contraption pose.
     */
    public static void render(
        PoseStack poseStack,
        Vec3 cameraPosition,
        float partialTick,
        @Nullable BlockPos poseAnchor,
        Iterable<Part> parts,
        float colorR,
        float colorG,
        float colorB
    ) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        try (ByteBufferBuilder byteBuffer = new ByteBufferBuilder(768 * 1024)) {
            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(byteBuffer);
            Frame frame = pushWorldFrame(poseStack, cameraPosition, partialTick, poseAnchor);
            try {
                renderFramed(poseStack, cameraPosition, bufferSource, dispatcher, frame, parts, colorR, colorG, colorB);
            } finally {
                poseStack.popPose();
            }
        }
    }

    /**
     * Depth + color passes into an existing buffer. Caller must have already pushed
     * {@link #pushWorldFrame} (or an equivalent local transform for items).
     */
    public static void renderFramed(
        PoseStack poseStack,
        Vec3 cameraPosition,
        MultiBufferSource.BufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        Frame frame,
        Iterable<Part> parts,
        float colorR,
        float colorG,
        float colorB
    ) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        float alpha = ModShaders.useCustomHologramShader() ? 1.0F : 0.9F;
        RenderSystem.setShaderColor(colorR, colorG, colorB, alpha);

        if (ModRenderTypes.useHologramDepthPrepass()) {
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(false, false, false, false);
            emitPass(poseStack, cameraPosition, bufferSource, dispatcher, frame, parts, true);
            bufferSource.endBatch();

            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(false);
            emitPass(poseStack, cameraPosition, bufferSource, dispatcher, frame, parts, false);
            bufferSource.endBatch();
        } else {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            emitPass(poseStack, cameraPosition, bufferSource, dispatcher, frame, parts, false);
            bufferSource.endBatch();
        }

        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Pushes camera (and optional Sable sub-level) transform. Caller must
     * {@link PoseStack#popPose()} when finished. Pair with {@link Frame#plotOrigin()} when
     * converting plot-storage coordinates.
     */
    public static Frame pushWorldFrame(
        PoseStack poseStack,
        Vec3 cameraPosition,
        float partialTick,
        @Nullable BlockPos poseAnchor
    ) {
        poseStack.pushPose();
        SableCompatClient.applyCameraAndSubLevelTransform(poseStack, poseAnchor, cameraPosition, partialTick);
        return new Frame(SableCompatClient.plotOrigin(poseAnchor, partialTick));
    }

    /**
     * One depth or color pass with camera translation applied here (overworld / no Sable).
     */
    public static void renderPass(
        PoseStack poseStack,
        Vec3 cameraPosition,
        MultiBufferSource.BufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        Iterable<Part> parts,
        boolean depthPass
    ) {
        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        emitPass(poseStack, cameraPosition, bufferSource, dispatcher, Frame.world(), parts, depthPass);
        poseStack.popPose();
    }

    /**
     * One depth or color pass; caller already applied camera / sub-level transforms.
     */
    public static void renderPassLocal(
        PoseStack poseStack,
        Vec3 cameraPosition,
        MultiBufferSource.BufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        Iterable<Part> parts,
        boolean depthPass,
        Frame frame
    ) {
        emitPass(poseStack, cameraPosition, bufferSource, dispatcher, frame, parts, depthPass);
    }

    public static void renderPassLocal(
        PoseStack poseStack,
        Vec3 cameraPosition,
        MultiBufferSource.BufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        Iterable<Part> parts,
        boolean depthPass,
        Vec3 plotOrigin
    ) {
        emitPass(poseStack, cameraPosition, bufferSource, dispatcher, new Frame(plotOrigin), parts, depthPass);
    }

    /**
     * Local-pose hologram meshes into an existing buffer (no world frame / depth prepass).
     * Prefer {@link #renderSolid} for held/GUI item previews.
     */
    public static void renderLocal(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        Iterable<Part> parts,
        int packedLight,
        int packedOverlay
    ) {
        boolean styleEffects = !ModShaders.useCustomHologramShader();
        float time = HologramEffectMath.shaderTimeSeconds();
        RenderType hologramType = ModRenderTypes.scanHologram();
        Map<BlockPos, BlockState> occupied = occupiedStates(parts);
        OccupiedBlockGetter cullLevel = new OccupiedBlockGetter(occupied);

        for (Part part : parts) {
            if (!SpecialBlockHandlers.shouldRenderPreview(part.state())) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(part.worldPos().getX(), part.worldPos().getY(), part.worldPos().getZ());
            int hiddenFaces = hiddenFaceMask(part, cullLevel);
            renderPart(
                poseStack,
                bufferSource,
                dispatcher,
                part,
                hologramType,
                false,
                styleEffects,
                0.0F,
                0.0F,
                0.0F,
                time,
                hiddenFaces,
                packedLight,
                packedOverlay
            );
            poseStack.popPose();
        }
    }

    /**
     * Fullbright solid mesh (scan/deploy below the plane), optional horizontal clip.
     */
    public static void renderSolid(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        BlockState state,
        @Nullable CompoundTag nbt,
        @Nullable Float localClipY
    ) {
        if (!SpecialBlockHandlers.shouldRenderPreview(state)) {
            return;
        }
        MultiBufferSource source;
        if (localClipY == null) {
            source = bufferSource;
        } else {
            float clipY = localClipY;
            source = renderType -> new PlaneClipVertexConsumer(bufferSource.getBuffer(renderType), clipY, true);
        }
        SpecialBlockHandler handler = SpecialBlockHandlers.find(state);
        if (handler != null && handler.render(
            dispatcher,
            state,
            poseStack,
            source,
            LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
            nbt,
            SpecialBlockHandler.RenderMode.SOLID
        )) {
            return;
        }
        dispatcher.renderSingleBlock(
            state,
            poseStack,
            source,
            LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY
        );
    }

    private static void emitPass(
        PoseStack poseStack,
        Vec3 cameraPosition,
        MultiBufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        Frame frame,
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
        Vec3 plotOrigin = frame.plotOrigin();

        // renderSingleBlock draws every model face; without neighbor culling, shared faces between
        // adjacent hologram parts stay coplanar and show as internal seams through the translucent shader.
        Map<BlockPos, BlockState> occupied = occupiedStates(parts);
        OccupiedBlockGetter cullLevel = new OccupiedBlockGetter(occupied);

        for (Part part : parts) {
            if (!SpecialBlockHandlers.shouldRenderPreview(part.state())) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(
                part.worldPos().getX() - plotOrigin.x,
                part.worldPos().getY() - plotOrigin.y,
                part.worldPos().getZ() - plotOrigin.z
            );
            int hiddenFaces = hiddenFaceMask(part, cullLevel);
            renderPart(
                poseStack,
                bufferSource,
                dispatcher,
                part,
                hologramType,
                depthPass,
                styleEffects,
                camX,
                camY,
                camZ,
                time,
                hiddenFaces,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY
            );
            poseStack.popPose();
        }
    }

    private static Map<BlockPos, BlockState> occupiedStates(Iterable<Part> parts) {
        Map<BlockPos, BlockState> occupied = new HashMap<>();
        for (Part part : parts) {
            occupied.put(part.worldPos(), part.state());
        }
        return occupied;
    }

    private static int hiddenFaceMask(Part part, OccupiedBlockGetter cullLevel) {
        int mask = 0;
        BlockPos pos = part.worldPos();
        BlockState state = part.state();
        for (Direction face : Direction.values()) {
            BlockPos neighborPos = pos.relative(face);
            if (!Block.shouldRenderFace(state, cullLevel, pos, face, neighborPos)) {
                mask |= 1 << face.ordinal();
            }
        }
        return mask;
    }

    private static void renderPart(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        Part part,
        RenderType hologramType,
        boolean depthPass,
        boolean styleEffects,
        float cameraX,
        float cameraY,
        float cameraZ,
        float time,
        int hiddenFaces,
        int packedLight,
        int packedOverlay
    ) {
        // Block-format meshes remap onto the hologram layer. Entity-format meshes (chest/bed BER)
        // keep their requested type for color so entity-atlas UVs stay valid; depth uses a matching
        // NEW_ENTITY depth-only layer so hollow interiors are occluded (not a stone cube proxy).
        MultiBufferSource source = requestedType -> {
            boolean blockFormat = isBlockVertexFormat(requestedType.format());
            RenderType targetType;
            if (blockFormat) {
                targetType = hologramType;
            } else if (depthPass) {
                targetType = ModRenderTypes.scanHologramEntityDepth();
            } else {
                targetType = requestedType;
            }
            VertexConsumer buffer = bufferSource.getBuffer(targetType);
            if (styleEffects) {
                buffer = new HologramStyleVertexConsumer(buffer, cameraX, cameraY, cameraZ, time);
            }
            if (part.clipY() != null) {
                buffer = new PlaneClipVertexConsumer(buffer, part.clipY(), part.keepBelow());
            }
            if (hiddenFaces != 0 && blockFormat) {
                buffer = new NeighborCullVertexConsumer(buffer, hiddenFaces);
            }
            return buffer;
        };

        SpecialBlockHandler.RenderMode mode = depthPass
            ? SpecialBlockHandler.RenderMode.HOLOGRAM_DEPTH
            : SpecialBlockHandler.RenderMode.HOLOGRAM;
        SpecialBlockHandler handler = SpecialBlockHandlers.find(part.state());
        if (handler != null && handler.render(
            dispatcher,
            part.state(),
            poseStack,
            source,
            packedLight,
            packedOverlay,
            part.nbt(),
            mode
        )) {
            return;
        }

        dispatcher.renderSingleBlock(
            part.state(),
            poseStack,
            source,
            packedLight,
            packedOverlay
        );
    }

    private static boolean isBlockVertexFormat(com.mojang.blaze3d.vertex.VertexFormat format) {
        if (format == DefaultVertexFormat.BLOCK) {
            return true;
        }
        // Cross-loader safe: Architectury may duplicate the BLOCK singleton.
        return format.getVertexSize() == DefaultVertexFormat.BLOCK.getVertexSize()
            && format.getElements().equals(DefaultVertexFormat.BLOCK.getElements());
    }

    /**
     * Minimal {@link BlockGetter} over hologram occupancy so {@link Block#shouldRenderFace}
     * can hide shared faces between adjacent parts.
     */
    private static final class OccupiedBlockGetter implements BlockGetter {
        private final Map<BlockPos, BlockState> occupied;

        OccupiedBlockGetter(Map<BlockPos, BlockState> occupied) {
            this.occupied = occupied;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return occupied.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return 0;
        }

        @Override
        public int getMinBuildHeight() {
            return 0;
        }
    }

    /**
     * Drops baked quads whose facing is marked hidden by neighbor occupancy.
     */
    static final class NeighborCullVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int hiddenFaces;

        NeighborCullVertexConsumer(VertexConsumer delegate, int hiddenFaces) {
            this.delegate = delegate;
            this.hiddenFaces = hiddenFaces;
        }

        private boolean hidden(Direction face) {
            return (hiddenFaces & (1 << face.ordinal())) != 0;
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
            if (hidden(quad.getDirection())) {
                return;
            }
            delegate.putBulkData(pose, quad, red, green, blue, alpha, packedLight, packedOverlay);
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
            if (hidden(quad.getDirection())) {
                return;
            }
            delegate.putBulkData(pose, quad, brightness, red, green, blue, alpha, lightmap, packedOverlay, colorize);
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
    }

    /**
     * Clips baked quads against a horizontal model-space plane (y = localClipY)
     * using Sutherland–Hodgman polygon clipping.
     */
    public static final class PlaneClipVertexConsumer implements VertexConsumer {
        private static final int MAX_CLIP_VERTS = 8;

        private final VertexConsumer delegate;
        private final float localClipY;
        private final boolean keepBelow;
        private final ClipVert[] input = new ClipVert[4];
        private final ClipVert[] clipA = new ClipVert[MAX_CLIP_VERTS];
        private final ClipVert[] clipB = new ClipVert[MAX_CLIP_VERTS];

        public PlaneClipVertexConsumer(VertexConsumer delegate, float localClipY, boolean keepBelow) {
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
