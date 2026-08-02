package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom render layers for scan hologram ghosts.
 * Extends {@link RenderType} so protected render-state shards are accessible.
 *
 * <p>Without a shader pack, holograms use a depth prepass then a translucent color pass so
 * blending cannot reveal farther hologram faces that were drawn earlier.
 *
 * <p>The depth prepass uses the vanilla solid program (not {@code scan_hologram}): the hologram
 * fragment shader is translucent/emissive and under Veil often fails to populate the depth
 * buffer, which makes every internal face show through. Color still uses the custom hologram
 * program for scanlines / sweeps.
 *
 * <p>Entity BER meshes (chest / bed / shulker) use a matching {@link DefaultVertexFormat#NEW_ENTITY}
 * depth layer so hollow interiors are occluded without remapping onto the block-atlas depth buffer.
 *
 * <p>When Iris/Oculus has a shader pack enabled, custom core shaders cannot participate in
 * Iris gbuffers (geometry would vanish). The Iris path uses {@link RenderType#translucentMovingBlock()}
 * (BLOCK format, Iris-remapped) plus {@link HologramStyleVertexConsumer}, drawn after deferred.
 */
public final class ModRenderTypes extends RenderType {
    private static final ShaderStateShard SCAN_HOLOGRAM_SHADER = new ShaderStateShard(ModShaders::getScanHologram);

    /** Dummy atlas for entity depth-only draws; UVs are irrelevant when color writes are off. */
    private static final TextureStateShard ENTITY_DEPTH_TEXTURE = new TextureStateShard(
        ResourceLocation.withDefaultNamespace("textures/entity/chest/normal.png"),
        false,
        false
    );

    private static final RenderType SCAN_HOLOGRAM_DEPTH = create(
        "precast_structure_scan_hologram_depth",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        786432,
        false,
        false,
        CompositeState.builder()
            .setLightmapState(LIGHTMAP)
            .setShaderState(RENDERTYPE_SOLID_SHADER)
            .setTextureState(BLOCK_SHEET_MIPPED)
            .setTransparencyState(NO_TRANSPARENCY)
            .setDepthTestState(LEQUAL_DEPTH_TEST)
            .setWriteMaskState(DEPTH_WRITE)
            .setCullState(CULL)
            .setOutputState(MAIN_TARGET)
            .createCompositeState(false)
    );

    private static final RenderType SCAN_HOLOGRAM_ENTITY_DEPTH = create(
        "precast_structure_scan_hologram_entity_depth",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS,
        786432,
        false,
        false,
        CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)
            .setTextureState(ENTITY_DEPTH_TEXTURE)
            .setTransparencyState(NO_TRANSPARENCY)
            .setLightmapState(LIGHTMAP)
            .setOverlayState(OVERLAY)
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

    /** Depth-only layer for chest/bed/shulker BER meshes (NEW_ENTITY format). */
    public static RenderType scanHologramEntityDepth() {
        return SCAN_HOLOGRAM_ENTITY_DEPTH;
    }

    public static RenderType scanHologram() {
        // translucentMovingBlock is the vanilla ghost-block layer Iris remaps correctly.
        return ModShaders.useCustomHologramShader() ? SCAN_HOLOGRAM : translucentMovingBlock();
    }

    /** Depth prepass is only valid with the custom core shader (non-Iris) path. */
    public static boolean useHologramDepthPrepass() {
        return ModShaders.useCustomHologramShader();
    }
}
