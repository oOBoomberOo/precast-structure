package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.jetbrains.annotations.Nullable;

/**
 * Pure policy for which render layer a hologram mesh should use.
 *
 * <p><b>Block models</b> (including Veil remapping them onto {@code entity_cutout} /
 * {@link DefaultVertexFormat#NEW_ENTITY}) must ALWAYS use the hologram block layer. Runtime
 * evidence: jungle_planks requested {@code entity_cutout} with {@code entityFormat=true} and were
 * drawn opaque via {@link Target#REQUESTED_ENTITY_COLOR}.
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

    /**
     * @deprecated Prefer {@link #resolve(Mode, boolean)}. Kept for tests covering the old
     *             entityFormat boolean; entityFormat=true must NOT steal block models.
     */
    @Deprecated
    public static Target resolve(boolean entityFormat, boolean depthPass) {
        // Historical API treated entityFormat as BER. That is unsafe under Veil (planks use
        // NEW_ENTITY). Callers must use Mode instead; this overload now always holograms.
        return Target.HOLOGRAM_BLOCK;
    }

    public static Target resolveEntityBer(boolean depthPass) {
        return depthPass ? Target.HOLOGRAM_ENTITY_DEPTH : Target.REQUESTED_ENTITY_COLOR;
    }

    /**
     * True only for entity-atlas meshes. Useful for diagnostics; do NOT gate block-model remap
     * on this under Veil (block models may report NEW_ENTITY).
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
