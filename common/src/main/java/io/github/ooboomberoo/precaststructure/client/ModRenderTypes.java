package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Custom render layers for scan hologram ghosts.
 * Extends {@link RenderType} so protected render-state shards are accessible.
 *
 * <p>Without a shader pack, holograms use a depth prepass then a translucent color pass so
 * blending cannot reveal farther hologram faces that were drawn earlier.
 *
 * <p>When Iris/Oculus has a shader pack enabled, custom core shaders cannot participate in
 * Iris gbuffers (geometry would vanish). The Iris path uses {@link RenderType#translucentMovingBlock()}
 * (BLOCK format, Iris-remapped) plus {@link HologramStyleVertexConsumer}, drawn after deferred.
 */
public final class ModRenderTypes extends RenderType {
    private static final ShaderStateShard SCAN_HOLOGRAM_SHADER = new ShaderStateShard(ModShaders::getScanHologram);

    private static final RenderType SCAN_HOLOGRAM_DEPTH = create(
        "precast_structure_scan_hologram_depth",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        786432,
        false,
        false,
        CompositeState.builder()
            .setShaderState(SCAN_HOLOGRAM_SHADER)
            .setTextureState(BLOCK_SHEET_MIPPED)
            .setTransparencyState(NO_TRANSPARENCY)
            .setWriteMaskState(DEPTH_WRITE)
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
        // translucentMovingBlock is the vanilla ghost-block layer Iris remaps correctly.
        return ModShaders.useCustomHologramShader() ? SCAN_HOLOGRAM : translucentMovingBlock();
    }

    /** Depth prepass is only valid with the custom core shader (non-Iris) path. */
    public static boolean useHologramDepthPrepass() {
        return ModShaders.useCustomHologramShader();
    }
}
