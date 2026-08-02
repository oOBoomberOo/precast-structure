package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom {@link RenderType} layers for scan and deploy hologram ghosts.
 * Extends {@link RenderType} so protected render-state shards are accessible.
 *
 * <h2>Default path (no Iris/Oculus shader pack)</h2>
 * Holograms draw in two passes that both use the hologram program:
 * <ol>
 *   <li><b>Depth prepass</b> ({@link #scanHologramDepth()}) — writes depth only.</li>
 *   <li><b>Color pass</b> ({@link #scanHologram()}) — translucent color, no depth write.</li>
 * </ol>
 * The split keeps translucent blending correct: nearer faces occlude farther ones instead of
 * showing through earlier-drawn geometry.
 *
 * <h2>Entity BER meshes</h2>
 * Chest, bed, shulker, skull, and similar entity renderers use matching per-atlas layers:
 * {@link #entityHologramDepth} ({@code DEPTH_WRITE}) then {@link #entityHologramColor}
 * ({@code COLOR_WRITE} + translucent). Depth and color should share the same atlas.
 *
 * <h2>Iris / Oculus path</h2>
 * Custom core shaders cannot join Iris gbuffers. With a shader pack enabled, holograms use
 * {@link RenderType#translucentMovingBlock()} plus {@link HologramStyleVertexConsumer}, drawn
 * after deferred shading. {@link #useHologramDepthPrepass()} is false on this path.
 */
public final class ModRenderTypes extends RenderType {
    /** Size in bytes for the RenderType buffer builder; matches vanilla entity/translucent layer sizing (768 KiB). */
    private static final int HOLOGRAM_BUFFER_SIZE = 786432;

    private static final ShaderStateShard SCAN_HOLOGRAM_SHADER = new ShaderStateShard(ModShaders::getScanHologram);
    private static final ShaderStateShard SCAN_HOLOGRAM_ENTITY_SHADER = new ShaderStateShard(ModShaders::getScanHologramEntity);

    private static final Map<ResourceLocation, RenderType> ENTITY_HOLOGRAM_DEPTH = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> ENTITY_HOLOGRAM_COLOR = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> ENTITY_HOLOGRAM_COLOR_FALLBACK = new ConcurrentHashMap<>();

    private static final RenderType SCAN_HOLOGRAM_DEPTH = create(
        "precast_structure_scan_hologram_depth",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        HOLOGRAM_BUFFER_SIZE,
        false,
        false,
        CompositeState.builder()
            .setLightmapState(LIGHTMAP)
            .setShaderState(SCAN_HOLOGRAM_SHADER)
            .setTextureState(BLOCK_SHEET_MIPPED)
            .setTransparencyState(NO_TRANSPARENCY)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(DEPTH_WRITE)
            .setCullState(CULL)
            .setOutputState(MAIN_TARGET)
            .createCompositeState(false)
    );

    private static final RenderType SCAN_HOLOGRAM = create(
        "precast_structure_scan_hologram",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        HOLOGRAM_BUFFER_SIZE,
        false,
        false,
        CompositeState.builder()
            .setLightmapState(LIGHTMAP)
            .setShaderState(SCAN_HOLOGRAM_SHADER)
            .setTextureState(BLOCK_SHEET_MIPPED)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setWriteMaskState(COLOR_WRITE)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setCullState(CULL)
            .setOutputState(MAIN_TARGET)
            .createCompositeState(false)
    );

    private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /** Block-format hologram depth prepass ({@code DEPTH_WRITE} only). */
    public static RenderType scanHologramDepth() {
        return SCAN_HOLOGRAM_DEPTH;
    }

    /**
     * Entity-format hologram depth prepass with a default chest atlas.
     * Prefer {@link #entityHologramDepth(ResourceLocation)} when the caller's atlas is known
     * so depth and color share the same texture.
     */
    public static RenderType scanHologramEntityDepth() {
        return entityHologramDepth(ResourceLocation.withDefaultNamespace("textures/entity/chest/normal.png"));
    }

    /**
     * Block-format hologram color pass.
     * Uses the custom hologram shader when available; otherwise
     * {@link RenderType#translucentMovingBlock()} for the Iris/Oculus path.
     */
    public static RenderType scanHologram() {
        return ModShaders.useCustomHologramShader() ? SCAN_HOLOGRAM : translucentMovingBlock();
    }

    /**
     * Entity-format hologram depth prepass bound to {@code atlas} ({@code DEPTH_WRITE} only).
     * Pair with {@link #entityHologramColor(ResourceLocation)} using the same atlas.
     */
    public static RenderType entityHologramDepth(ResourceLocation atlas) {
        return ENTITY_HOLOGRAM_DEPTH.computeIfAbsent(atlas, ModRenderTypes::createEntityHologramDepth);
    }

    /**
     * Translucent entity-format hologram color pass bound to {@code atlas}.
     * Writes color only ({@code COLOR_WRITE}); depth comes from {@link #entityHologramDepth}.
     * Uses the {@code scan_hologram_entity} program when available.
     */
    public static RenderType entityHologramColor(ResourceLocation atlas) {
        if (ModShaders.useCustomEntityHologramShader()) {
            return ENTITY_HOLOGRAM_COLOR.computeIfAbsent(atlas, ModRenderTypes::createEntityHologramColor);
        }
        return ENTITY_HOLOGRAM_COLOR_FALLBACK.computeIfAbsent(atlas, ModRenderTypes::createEntityHologramColorFallback);
    }

    private static RenderType createEntityHologramDepth(ResourceLocation atlas) {
        return create(
            "precast_structure_entity_hologram_depth/" + atlas.toDebugFileName(),
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            HOLOGRAM_BUFFER_SIZE,
            false,
            false,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)
                .setTextureState(new TextureStateShard(atlas, false, false))
                .setTransparencyState(NO_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setWriteMaskState(DEPTH_WRITE)
                .setCullState(CULL)
                .setOutputState(MAIN_TARGET)
                .createCompositeState(false)
        );
    }

    private static RenderType createEntityHologramColor(ResourceLocation atlas) {
        return create(
            "precast_structure_entity_hologram_color/" + atlas.toDebugFileName(),
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            HOLOGRAM_BUFFER_SIZE,
            false,
            true,
            CompositeState.builder()
                .setShaderState(SCAN_HOLOGRAM_ENTITY_SHADER)
                .setTextureState(new TextureStateShard(atlas, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE)
                .setCullState(CULL)
                .setOutputState(MAIN_TARGET)
                .createCompositeState(true)
        );
    }

    private static RenderType createEntityHologramColorFallback(ResourceLocation atlas) {
        return create(
            "precast_structure_entity_hologram_color_fallback/" + atlas.toDebugFileName(),
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            HOLOGRAM_BUFFER_SIZE,
            false,
            true,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(atlas, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE)
                .setCullState(CULL)
                .setOutputState(MAIN_TARGET)
                .createCompositeState(true)
        );
    }

    /** {@code true} when the default depth-prepass + color-pass path is active (no Iris shader pack). */
    public static boolean useHologramDepthPrepass() {
        return ModShaders.useCustomHologramShader();
    }
}
