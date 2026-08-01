package io.github.ooboomberoo.precaststructure.structure;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import io.github.ooboomberoo.precaststructure.config.ModConfig;
import io.github.ooboomberoo.precaststructure.network.ModNetworking;
import io.github.ooboomberoo.precaststructure.registry.ModSounds;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public final class StructureDeploymentManager {
    private static final Map<ResourceKey<Level>, Map<UUID, StructureDeployment>> SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, StructureDeployment> CLIENT = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CLIENT_REMOVE_GRACE = new ConcurrentHashMap<>();
    @Nullable
    private static ResourceKey<Level> clientDimension;

    private StructureDeploymentManager() {
    }

    public static void init() {
        TickEvent.SERVER_LEVEL_POST.register(StructureDeploymentManager::tickLevel);
        LifecycleEvent.SERVER_LEVEL_UNLOAD.register(StructureDeploymentManager::onLevelUnload);
        LifecycleEvent.SERVER_STOPPED.register(server -> SERVER.clear());
        PlayerEvent.PLAYER_JOIN.register(StructureDeploymentManager::syncAllToPlayer);
        PlayerEvent.CHANGE_DIMENSION.register((player, oldLevel, newLevel) -> syncAllToPlayer(player));
    }

    public static StructureDeployment start(ServerLevel level, BlockPos origin, Direction facing, StructureBlueprint blueprint) {
        StructureDeployment deployment = StructureDeployment.create(origin, facing, blueprint, level.getGameTime());
        HologramCollision.placeForBlueprint(level, origin, blueprint, facing);
        deploymentsFor(level).put(deployment.id(), deployment);
        ModNetworking.sendDeployAdd(level, deployment);
        return deployment;
    }

    public static Collection<StructureDeployment> clientDeployments() {
        return CLIENT.values();
    }

    public static void clientAdd(StructureDeployment deployment) {
        CLIENT_REMOVE_GRACE.remove(deployment.id());
        CLIENT.put(deployment.id(), deployment);
    }

    public static void clientRemove(UUID id) {
        if (CLIENT.containsKey(id)) {
            int grace = Math.max(0, ModConfig.get().deploy.clientGraceTicks);
            if (grace <= 0) {
                CLIENT.remove(id);
            } else {
                CLIENT_REMOVE_GRACE.put(id, grace);
            }
        }
    }

    public static boolean clientIsFinishing(UUID id) {
        return CLIENT_REMOVE_GRACE.containsKey(id);
    }

    public static void clientTick(@Nullable Level level) {
        if (level == null) {
            if (!CLIENT.isEmpty() || !CLIENT_REMOVE_GRACE.isEmpty()) {
                clientClear();
            }
            clientDimension = null;
            return;
        }

        ResourceKey<Level> dimension = level.dimension();
        if (clientDimension != null && !clientDimension.equals(dimension)) {
            clientClear();
        }
        clientDimension = dimension;

        if (CLIENT_REMOVE_GRACE.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> iterator = CLIENT_REMOVE_GRACE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                CLIENT.remove(entry.getKey());
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    public static void clientClear() {
        CLIENT.clear();
        CLIENT_REMOVE_GRACE.clear();
    }

    public static boolean clientOverlaps(AABB region) {
        return anyOverlaps(CLIENT.values(), region);
    }

    /** True if {@code region} intersects any in-progress deploy in this level (client or server). */
    public static boolean overlapsActiveDeploy(Level level, AABB region) {
        if (level.isClientSide()) {
            return clientOverlaps(region);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        Map<UUID, StructureDeployment> deployments = SERVER.get(serverLevel.dimension());
        if (deployments == null || deployments.isEmpty()) {
            return false;
        }
        return anyOverlaps(deployments.values(), region);
    }

    private static boolean anyOverlaps(Collection<StructureDeployment> deployments, AABB region) {
        for (StructureDeployment deployment : deployments) {
            if (deployment.bounds().intersects(region)) {
                return true;
            }
        }
        return false;
    }

    private static void onLevelUnload(ServerLevel level) {
        Map<UUID, StructureDeployment> deployments = SERVER.remove(level.dimension());
        if (deployments == null || deployments.isEmpty()) {
            return;
        }
        for (StructureDeployment deployment : deployments.values()) {
            if (!deployment.hasPlaced()) {
                deployment.placeAll(level);
            }
        }
    }

    private static void tickLevel(ServerLevel level) {
        Map<UUID, StructureDeployment> deployments = SERVER.get(level.dimension());
        if (deployments == null || deployments.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, StructureDeployment>> iterator = deployments.entrySet().iterator();
        while (iterator.hasNext()) {
            StructureDeployment deployment = iterator.next().getValue();

            if (level.getGameTime() % ModConfig.get().deploy.soundIntervalTicks == 0) {
                level.playSound(
                    null,
                    deployment.origin(),
                    ModSounds.SCANNING.get(),
                    SoundSource.BLOCKS,
                    0.55F,
                    1.15F
                );
            }

            if (deployment.isComplete(level)) {
                // Place this tick, remove client ghosts next tick so block updates arrive first.
                if (!deployment.hasPlaced()) {
                    deployment.placeAll(level);
                } else {
                    iterator.remove();
                    ModNetworking.sendDeployRemove(level, deployment.id());
                }
            }
        }
    }

    private static void syncAllToPlayer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Map<UUID, StructureDeployment> deployments = SERVER.get(level.dimension());
        if (deployments == null) {
            return;
        }
        for (StructureDeployment deployment : deployments.values()) {
            ModNetworking.sendDeployAdd(player, deployment);
        }
    }

    private static Map<UUID, StructureDeployment> deploymentsFor(ServerLevel level) {
        return SERVER.computeIfAbsent(level.dimension(), key -> new ConcurrentHashMap<>());
    }
}
