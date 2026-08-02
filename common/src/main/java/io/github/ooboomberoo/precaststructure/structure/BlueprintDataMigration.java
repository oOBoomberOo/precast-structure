package io.github.ooboomberoo.precaststructure.structure;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

/**
 * Moves legacy {@code custom_data.PrecastStructure} onto
 * {@link io.github.ooboomberoo.precaststructure.registry.ModDataComponents#BLUEPRINT_STRUCTURE}
 * when a world/server starts and when players join.
 */
public final class BlueprintDataMigration {
    private BlueprintDataMigration() {
    }

    public static void init() {
        LifecycleEvent.SERVER_STARTED.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                migratePlayer(player);
            }
        });
        PlayerEvent.PLAYER_JOIN.register(BlueprintDataMigration::migratePlayer);
    }

    private static void migratePlayer(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        BlueprintItemData.migrateInventory(inventory);
        // Ender chest is a separate container on the player.
        for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
            BlueprintItemData.migrateStack(player.getEnderChestInventory().getItem(i));
        }
    }
}
