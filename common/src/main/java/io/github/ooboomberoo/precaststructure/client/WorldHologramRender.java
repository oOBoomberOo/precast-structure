package io.github.ooboomberoo.precaststructure.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;

/**
 * Shared entry point for world-space hologram overlays (scan / deploy / placement ghost).
 */
public final class WorldHologramRender {
    private WorldHologramRender() {
    }

    public static void renderAll(PoseStack poseStack, Vec3 cameraPosition, float partialTick) {
        if (ShaderCompat.isRenderingShadowPass()) {
            return;
        }
        StructureScanRenderer.render(poseStack, cameraPosition, partialTick);
        StructureDeployRenderer.render(poseStack, cameraPosition, partialTick);
        StructureGhostRenderer.render(poseStack, cameraPosition);
    }
}
