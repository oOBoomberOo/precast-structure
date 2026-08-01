package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Custom render layers for scan hologram ghosts.
 * Extends {@link RenderType} so protected render-state shards are accessible.
 *
 * <p>Holograms use a depth prepass then a color pass so translucent blending cannot
 * reveal farther hologram faces that were drawn earlier.
 */
public final class ModRenderTypes extends RenderType {
    private static final ShaderStateShard SCAN_HOLOGRAM_SHADER = new ShaderStateShard(ModShaders::getScanHologram);

    /** Writes nearest hologram depth only (order-independent occlusion). */
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
            .setWriteMaskState(DEPTH_WRITE)
            .setOutputState(MAIN_TARGET)
            .createCompositeState(false)
    );

    /** Colors only the nearest surface established by {@link #SCAN_HOLOGRAM_DEPTH}. */
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
            .setOutputState(MAIN_TARGET)
            .createCompositeState(false)
    );

    private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    public static RenderType scanHologramDepth() {
        return SCAN_HOLOGRAM_DEPTH;
    }

    public static RenderType scanHologram() {
        return SCAN_HOLOGRAM;
    }
}
