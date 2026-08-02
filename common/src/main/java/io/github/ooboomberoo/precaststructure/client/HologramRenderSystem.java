package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.ooboomberoo.precaststructure.client.special.BlockEntityPreviewRenderer;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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
            // BER ModelPart verts are already pose-transformed; wipe Y must match that space.
            float outputClipY = poseStack.last().pose().transformPosition(0.0F, clipY, 0.0F, new org.joml.Vector3f()).y;
            source = renderType -> new PlaneClipVertexConsumer(
                bufferSource.getBuffer(renderType), clipY, outputClipY, true
            );
        }
        renderPreviewMesh(
            dispatcher,
            state,
            poseStack,
            source,
            source,
            LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
            nbt,
            SpecialBlockHandler.RenderMode.SOLID
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
        // Sanitize lectern has_book / inventories for holograms even on old blueprints.
        BlockState drawState = SpecialBlockHandlers.sanitizeCapturedState(part.state());
        CompoundTag drawNbt = SpecialBlockHandlers.sanitizeCaptured(drawState, part.nbt());
        Part drawPart = new Part(part.worldPos(), drawState, drawNbt, part.clipY(), part.keepBelow());

        // Block models always use the hologram layer. Under Veil, planks may request
        // entity_cutout / NEW_ENTITY; treating that as BER drew opaque vanilla cutout instead.
        // True BER special handlers use a separate entity-preserving buffer source below.
        MultiBufferSource entityBerSource = requestedType -> {
            HologramLayerPolicy.Target target = HologramLayerPolicy.resolve(
                HologramLayerPolicy.Mode.ENTITY_BER, depthPass
            );
            return wrapBuffer(
                bufferSource,
                drawPart,
                hologramType,
                target,
                requestedType,
                styleEffects,
                cameraX,
                cameraY,
                cameraZ,
                time,
                hiddenFaces,
                poseStack
            );
        };

        SpecialBlockHandler.RenderMode mode = depthPass
            ? SpecialBlockHandler.RenderMode.HOLOGRAM_DEPTH
            : SpecialBlockHandler.RenderMode.HOLOGRAM;
        MultiBufferSource modelSource = requestedType -> wrapBuffer(
            bufferSource,
            drawPart,
            hologramType,
            HologramLayerPolicy.resolve(HologramLayerPolicy.Mode.BLOCK_MODEL, depthPass),
            requestedType,
            styleEffects,
            cameraX,
            cameraY,
            cameraZ,
            time,
            hiddenFaces,
            poseStack
        );
        renderPreviewMesh(
            dispatcher,
            drawState,
            poseStack,
            modelSource,
            entityBerSource,
            packedLight,
            packedOverlay,
            drawNbt,
            mode
        );
    }

    /**
     * Special handlers first (Create kinetics, custom overrides). Otherwise generic path:
     * {@link RenderShape#ENTITYBLOCK_ANIMATED} / {@code INVISIBLE} → BER only; model-shaped
     * entity blocks → baked model then optional BER overlay (enchanting table, lectern, …).
     */
    private static void renderPreviewMesh(
        BlockRenderDispatcher dispatcher,
        BlockState state,
        PoseStack poseStack,
        MultiBufferSource modelSource,
        MultiBufferSource berSource,
        int packedLight,
        int packedOverlay,
        @Nullable CompoundTag nbt,
        SpecialBlockHandler.RenderMode mode
    ) {
        SpecialBlockHandler handler = SpecialBlockHandlers.find(state);
        if (handler != null && handler.render(
            dispatcher,
            state,
            poseStack,
            berSource,
            packedLight,
            packedOverlay,
            nbt,
            mode
        )) {
            return;
        }

        if (BlockEntityPreviewRenderer.isBerPrimary(state)) {
            if (BlockEntityPreviewRenderer.render(
                state, poseStack, berSource, packedLight, packedOverlay, nbt
            )) {
                return;
            }
        }

        dispatcher.renderSingleBlock(state, poseStack, modelSource, packedLight, packedOverlay);

        if (!BlockEntityPreviewRenderer.isBerPrimary(state)) {
            BlockEntityPreviewRenderer.render(
                state, poseStack, berSource, packedLight, packedOverlay, nbt
            );
        }
    }

    private static VertexConsumer wrapBuffer(
        MultiBufferSource bufferSource,
        Part part,
        RenderType hologramType,
        HologramLayerPolicy.Target target,
        RenderType requestedType,
        boolean styleEffects,
        float cameraX,
        float cameraY,
        float cameraZ,
        float time,
        int hiddenFaces,
        PoseStack poseStack
    ) {
        RenderType targetType = switch (target) {
            case HOLOGRAM_BLOCK -> hologramType;
            case HOLOGRAM_ENTITY_DEPTH -> entityHologramDepthType(requestedType);
            case REQUESTED_ENTITY_COLOR -> entityHologramColorType(requestedType);
        };
        VertexConsumer buffer = bufferSource.getBuffer(targetType);
        // BER color uses scan_hologram_entity (fragment animation). CPU style is Iris fallback only.
        boolean applyStyle = styleEffects
            || (target == HologramLayerPolicy.Target.REQUESTED_ENTITY_COLOR
                && !ModShaders.useCustomEntityHologramShader());
        if (applyStyle) {
            buffer = new HologramStyleVertexConsumer(buffer, cameraX, cameraY, cameraZ, time);
        }
        if (part.clipY() != null) {
            float modelClipY = part.clipY();
            float outputClipY = poseStack.last().pose()
                .transformPosition(0.0F, modelClipY, 0.0F, new org.joml.Vector3f()).y;
            buffer = new PlaneClipVertexConsumer(buffer, modelClipY, outputClipY, part.keepBelow());
        }
        if (hiddenFaces != 0 && target == HologramLayerPolicy.Target.HOLOGRAM_BLOCK) {
            buffer = new NeighborCullVertexConsumer(buffer, hiddenFaces);
        }
        return buffer;
    }

    private static RenderType entityHologramDepthType(RenderType requestedType) {
        // Sign/banner text uses POSITION_COLOR_TEX_LIGHTMAP (no UV1/Normal). Remapping those
        // onto NEW_ENTITY hologram layers crashes BufferBuilder ("Missing elements: UV1, Normal").
        if (!HologramLayerPolicy.isEntityVertexFormat(requestedType.format())) {
            return requestedType;
        }
        ResourceLocation atlas = textureAtlasFromRenderType(requestedType);
        if (atlas != null) {
            return ModRenderTypes.entityHologramDepth(atlas);
        }
        return ModRenderTypes.scanHologramEntityDepth();
    }

    /**
     * Translucent COLOR_WRITE-only layer using the BER atlas (not vanilla entity_translucent,
     * which also writes depth and z-fights the prepass).
     */
    private static RenderType entityHologramColorType(RenderType requestedType) {
        if (!HologramLayerPolicy.isEntityVertexFormat(requestedType.format())) {
            return requestedType;
        }
        ResourceLocation atlas = textureAtlasFromRenderType(requestedType);
        if (atlas != null) {
            return ModRenderTypes.entityHologramColor(atlas);
        }
        return requestedType;
    }

    @Nullable
    private static ResourceLocation textureAtlasFromRenderType(RenderType type) {
        String text = type.toString();
        int optional = text.indexOf("Optional[");
        if (optional < 0) {
            return null;
        }
        int start = optional + "Optional[".length();
        int end = text.indexOf(']', start);
        if (end <= start) {
            return null;
        }
        try {
            return ResourceLocation.parse(text.substring(start, end));
        } catch (RuntimeException ignored) {
            return null;
        }
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
}
