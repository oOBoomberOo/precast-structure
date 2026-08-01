package io.github.ooboomberoo.precaststructure.forge;

import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.client.ShaderCompat;
import io.github.ooboomberoo.precaststructure.client.WorldHologramRender;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PrecastStructureMod.MOD_ID, value = Dist.CLIENT)
public final class PrecastStructureForgeClientEvents {
    private PrecastStructureForgeClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        boolean late = ShaderCompat.shouldUseLateWorldOverlayPass();
        // AFTER_TRANSLUCENT_BLOCKS is unreliable with fabulous targets; AFTER_PARTICLES is after deferred.
        RenderLevelStageEvent.Stage expected = late
            ? RenderLevelStageEvent.Stage.AFTER_PARTICLES
            : RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if (event.getStage() != expected) {
            return;
        }
        float partialTick = event.getPartialTick();
        WorldHologramRender.renderAll(
            event.getPoseStack(),
            event.getCamera().getPosition(),
            partialTick
        );
    }
}
