package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom render layers for scan hologram ghosts.
 * Extends {@link RenderType} so protected render-state shards are accessible.
 *
 * <p>Without a shader pack, holograms use a depth prepass then a translucent color pass so
 * blending cannot reveal farther hologram faces that were drawn earlier.
 *
 * <p>The depth prepass also uses {@code scan_hologram} (not {@code rendertype_solid}). A prior
 * solid-shader depth seed looked correct in isolation but under Veil/Sable WriteMask/colorMask
 * often fails, so the depth pass painted fully opaque real-looking blocks and stole the ghost.
 * Neighbor-face culling covers most internal seams; color still uses the same hologram program.
 *
 * <p>Entity BER meshes (chest / bed / shulker / skull) use per-atlas NEW_ENTITY depth (DEPTH_WRITE)
 * and color (COLOR_WRITE + translucent) layers. Vanilla {@code entity_translucent} also writes
 * depth, which z-fights the prepass — so BER color must use COLOR_WRITE only.
 *
 * <p>When Iris/Oculus has a shader pack enabled, custom core shaders cannot participate in
 * Iris gbuffers (geometry would vanish). The Iris path uses {@link RenderType#translucentMovingBlock()}
 * (BLOCK format, Iris-remapped) plus {@link HologramStyleVertexConsumer}, drawn after deferred.
 */
public final class ModRenderTypes extends RenderType {
    private static final ShaderStateShard SCAN_HOLOGRAM_SHADER = new ShaderStateShard(ModShaders::getScanHologram);
    private static final ShaderStateShard SCAN_HOLOGRAM_ENTITY_SHADER = new ShaderStateShard(ModShaders::getScanHologramEntity);

    private static final Map<ResourceLocation, RenderType> ENTITY_HOLOGRAM_DEPTH = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> ENTITY_HOLOGRAM_COLOR = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, RenderType> ENTITY_HOLOGRAM_COLOR_FALLBACK = new ConcurrentHashMap<>();

    private static final RenderType SCAN_HOLOGRAM_DEPTH = create(
        "precast_structure_scan_hologram_depth",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        786432,
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
        786432,
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

    public static RenderType scanHologramDepth() {
        return SCAN_HOLOGRAM_DEPTH;
    }

    /**
     * Fallback entity depth layer when the BER atlas cannot be recovered from the requested type.
     */
    public static RenderType scanHologramEntityDepth() {
        return entityHologramDepth(ResourceLocation.withDefaultNamespace("textures/entity/chest/normal.png"));
    }

    public static RenderType scanHologram() {
        // translucentMovingBlock is the vanilla ghost-block layer Iris remaps correctly.
        return ModShaders.useCustomHologramShader() ? SCAN_HOLOGRAM : translucentMovingBlock();
    }

    /** Depth-only BER layer using the real entity atlas (avoids wrong-UV depth vs color). */
    public static RenderType entityHologramDepth(ResourceLocation atlas) {
        return ENTITY_HOLOGRAM_DEPTH.computeIfAbsent(atlas, ModRenderTypes::createEntityHologramDepth);
    }

    /**
     * Translucent BER color layer: COLOR_WRITE only so it does not z-fight the depth prepass.
     * Uses {@code scan_hologram_entity} when available (same animation as block holograms).
     */
    public static RenderType entityHologramColor(ResourceLocation atlas) {
        if (ModShaders.useCustomEntityHologramShader()) {
            return ENTITY_HOLOGRAM_COLOR.computeIfAbsent(atlas, ModRenderTypes::createEntityHologramColor);
        }
        return ENTITY_HOLOGRAM_COLOR_FALLBACK.computeIfAbsent(atlas, ModRenderTypes::createEntityHologramColorFallback);
    }

    /** @deprecated use {@link #entityHologramColor(ResourceLocation)} */
    @Deprecated
    public static RenderType entityHologram(ResourceLocation atlas) {
        return entityHologramColor(atlas);
    }

    private static RenderType createEntityHologramDepth(ResourceLocation atlas) {
        return create(
            "precast_structure_entity_hologram_depth/" + atlas.toDebugFileName(),
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            786432,
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
            786432,
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
            786432,
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

    /** Depth prepass is only valid with the custom core shader (non-Iris) path. */
    public static boolean useHologramDepthPrepass() {
        return ModShaders.useCustomHologramShader();
    }
}
