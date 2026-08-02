package io.github.ooboomberoo.precaststructure.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import io.github.ooboomberoo.precaststructure.structure.special.SpecialBlockHandlers;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Locks hologram pipeline invariants that previously regressed to fully opaque solid ghosts.
 */
class HologramPipelineTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        SpecialBlockHandlers.bootstrap();
    }

    @Test
    void blockModelModeAlwaysRemapsToHologramEvenIfEntityFormat() {
        // Veil asks for entity_cutout / NEW_ENTITY for jungle_planks — must still hologram.
        assertEquals(
            HologramLayerPolicy.Target.HOLOGRAM_BLOCK,
            HologramLayerPolicy.resolve(HologramLayerPolicy.Mode.BLOCK_MODEL, false)
        );
        assertEquals(
            HologramLayerPolicy.Target.HOLOGRAM_BLOCK,
            HologramLayerPolicy.resolve(HologramLayerPolicy.Mode.BLOCK_MODEL, true)
        );
        assertEquals(
            HologramLayerPolicy.Target.HOLOGRAM_BLOCK,
            HologramLayerPolicy.resolve(true, false),
            "legacy resolve(entityFormat=true) must not steal block models"
        );
    }

    @Test
    void entityBerModePreservesEntityLayers() {
        assertEquals(
            HologramLayerPolicy.Target.REQUESTED_ENTITY_COLOR,
            HologramLayerPolicy.resolve(HologramLayerPolicy.Mode.ENTITY_BER, false)
        );
        assertEquals(
            HologramLayerPolicy.Target.HOLOGRAM_ENTITY_DEPTH,
            HologramLayerPolicy.resolve(HologramLayerPolicy.Mode.ENTITY_BER, true)
        );
    }

    @Test
    void blockFormatIsNeverTreatedAsEntity() {
        assertFalse(HologramLayerPolicy.isEntityVertexFormat(DefaultVertexFormat.BLOCK));
    }

    @Test
    void entityFormatDetectionStillWorksForDiagnostics() {
        assertTrue(HologramLayerPolicy.isEntityVertexFormat(DefaultVertexFormat.NEW_ENTITY));
        assertFalse(HologramLayerPolicy.isEntityVertexFormat(null));
    }

    @Test
    void architecturyStyleDuplicateBlockFormatStillMapsToHologram() {
        VertexFormat duplicateBlock = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV2", VertexFormatElement.UV2)
            .add("Normal", VertexFormatElement.NORMAL)
            .padding(1)
            .build();

        assertFalse(HologramLayerPolicy.isEntityVertexFormat(duplicateBlock));
        assertEquals(
            HologramLayerPolicy.Target.HOLOGRAM_BLOCK,
            HologramLayerPolicy.resolve(HologramLayerPolicy.Mode.BLOCK_MODEL, false)
        );
    }

    @Test
    void depthAndColorHologramLayersAreDistinct() {
        RenderType depth = ModRenderTypes.scanHologramDepth();
        RenderType color = ModRenderTypes.scanHologram();
        assertNotNull(depth);
        assertNotNull(color);
        assertNotEquals(depth, color);
        assertNotEquals(RenderType.solid(), depth, "depth prepass must not be vanilla solid");
        assertNotEquals(RenderType.solid(), color, "color pass must not be vanilla solid");
        assertTrue(
            depth.toString().contains("scan_hologram") || depth.toString().contains("precast_structure"),
            () -> "unexpected depth layer: " + depth
        );
    }

    @Test
    void ghostPreviewStaysOnHologramPipeline() {
        assertTrue(StructureGhostRenderer.usesHologramPipeline());
    }

    @Test
    void normalBlocksAreNotStolenBySpecialHandlers() {
        assertTrue(SpecialBlockHandlers.find(Blocks.JUNGLE_PLANKS.defaultBlockState()) == null);
        assertTrue(SpecialBlockHandlers.find(Blocks.DARK_OAK_PLANKS.defaultBlockState()) == null);
        assertNotNull(SpecialBlockHandlers.find(Blocks.CHEST.defaultBlockState()));
    }
}
