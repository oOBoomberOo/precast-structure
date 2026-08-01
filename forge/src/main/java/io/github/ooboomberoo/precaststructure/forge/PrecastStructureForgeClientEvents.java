package io.github.ooboomberoo.precaststructure.forge;

import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.client.StructureGhostRenderer;
import io.github.ooboomberoo.precaststructure.client.StructureScanRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = PrecastStructureMod.MOD_ID, value = Dist.CLIENT)
public final class PrecastStructureForgeClientEvents {
    private PrecastStructureForgeClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        // Depth buffer still has opaque/cutout world geometry (fences, etc.).
        StructureScanRenderer.render(
            event.getPoseStack(),
            event.getCamera().getPosition(),
            event.getPartialTick().getGameTimeDeltaPartialTick(false)
        );
        StructureGhostRenderer.render(event.getPoseStack(), event.getCamera().getPosition());
    }
}
