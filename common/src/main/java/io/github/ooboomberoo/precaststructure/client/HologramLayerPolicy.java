package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.jetbrains.annotations.Nullable;

/**
 * Pure policy for which render layer a hologram mesh should use.
 *
 * <p><b>Block models</b> always resolve to {@link Target#HOLOGRAM_BLOCK}, even when Veil remaps
 * them onto {@code entity_cutout} / {@link DefaultVertexFormat#NEW_ENTITY}. Vertex format alone
 * is not a reliable signal for layer choice under that remapping.
 *
 * <p><b>True BER meshes</b> (chest / bed / shulker) keep entity atlas layers via
 * {@link #resolveEntityBer(boolean)}.
 */
public final class HologramLayerPolicy {
    public enum Target {
        /** Block-atlas hologram color or block depth layer supplied by the caller. */
        HOLOGRAM_BLOCK,
        /** Depth-only NEW_ENTITY layer for BER meshes. */
        HOLOGRAM_ENTITY_DEPTH,
        /** Caller's requested entity RenderType (entity atlas UVs). */
        REQUESTED_ENTITY_COLOR
    }

    public enum Mode {
        /** {@code renderSingleBlock} / baked block models — always hologram. */
        BLOCK_MODEL,
        /** Chest/bed/shulker BER — preserve entity atlas types. */
        ENTITY_BER
    }

    private HologramLayerPolicy() {
    }

    public static Target resolve(Mode mode, boolean depthPass) {
        if (mode == Mode.ENTITY_BER) {
            return resolveEntityBer(depthPass);
        }
        return Target.HOLOGRAM_BLOCK;
    }

    public static Target resolveEntityBer(boolean depthPass) {
        return depthPass ? Target.HOLOGRAM_ENTITY_DEPTH : Target.REQUESTED_ENTITY_COLOR;
    }

    /**
     * Whether {@code format} matches {@link DefaultVertexFormat#NEW_ENTITY}.
     * Block models remapped by Veil may also report this format, so callers must not use this
     * alone to choose between block and entity hologram layers.
     */
    public static boolean isEntityVertexFormat(@Nullable VertexFormat format) {
        if (format == null) {
            return false;
        }
        if (format == DefaultVertexFormat.NEW_ENTITY) {
            return true;
        }
        return format.getVertexSize() == DefaultVertexFormat.NEW_ENTITY.getVertexSize()
            && format.getElements().equals(DefaultVertexFormat.NEW_ENTITY.getElements());
    }
}
