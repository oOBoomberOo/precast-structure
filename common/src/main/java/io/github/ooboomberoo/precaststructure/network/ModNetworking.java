package io.github.ooboomberoo.precaststructure.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.utils.Env;
import dev.architectury.utils.EnvExecutor;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.block.entity.StructureScannerBlockEntity;
import io.github.ooboomberoo.precaststructure.menu.StructureScannerMenu;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import io.github.ooboomberoo.precaststructure.structure.StructureDeployment;
import io.github.ooboomberoo.precaststructure.structure.StructureDeploymentManager;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class ModNetworking {
  public static final ResourceLocation SCANNER_ACTION =
      ResourceLocation.fromNamespaceAndPath(PrecastStructureMod.MOD_ID, "scanner_action");
  public static final ResourceLocation DEPLOY_ADD =
      ResourceLocation.fromNamespaceAndPath(PrecastStructureMod.MOD_ID, "deploy_add");
  public static final ResourceLocation DEPLOY_REMOVE =
      ResourceLocation.fromNamespaceAndPath(PrecastStructureMod.MOD_ID, "deploy_remove");

  private ModNetworking() {}

  public static void register() {
    // Dedicated server only: client registerReceiver already registers these S2C types.
    EnvExecutor.runInEnv(
        Env.SERVER,
        () ->
            () -> {
              NetworkManager.registerS2CPayloadType(DEPLOY_ADD);
              NetworkManager.registerS2CPayloadType(DEPLOY_REMOVE);
            });

    NetworkManager.registerReceiver(
        NetworkManager.clientToServer(),
        SCANNER_ACTION,
        (buf, context) -> {
          BlockPos pos = buf.readBlockPos();
          String structureName = buf.readUtf(StructureScannerBlockEntity.MAX_NAME_LENGTH);
          context.queue(
              () -> {
                if (!(context.getPlayer() instanceof ServerPlayer serverPlayer)) {
                  return;
                }
                if (!(serverPlayer.containerMenu instanceof StructureScannerMenu menu)
                    || !menu.getBlockPos().equals(pos)) {
                  return;
                }
                StructureScannerBlockEntity scanner = menu.getScanner();
                if (scanner == null
                    && serverPlayer.level().getBlockEntity(pos)
                        instanceof StructureScannerBlockEntity levelScanner) {
                  scanner = levelScanner;
                }
                if (scanner == null) {
                  return;
                }
                scanner.setStructureName(structureName);
                scanner.scanStructure(serverPlayer);
              });
        });
  }

  public static void registerClient() {
    NetworkManager.registerReceiver(
        NetworkManager.serverToClient(),
        DEPLOY_ADD,
        (buf, context) -> {
          StructureDeployment deployment = readDeployAdd(buf);
          context.queue(() -> StructureDeploymentManager.clientAdd(deployment));
        });
    NetworkManager.registerReceiver(
        NetworkManager.serverToClient(),
        DEPLOY_REMOVE,
        (buf, context) -> {
          UUID id = buf.readUUID();
          context.queue(() -> StructureDeploymentManager.clientRemove(id));
        });
  }

  public static void sendScannerAction(BlockPos pos, String structureName) {
    RegistryFriendlyByteBuf buf =
        new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    buf.writeBlockPos(pos);
    buf.writeUtf(structureName, StructureScannerBlockEntity.MAX_NAME_LENGTH);
    NetworkManager.sendToServer(SCANNER_ACTION, buf);
  }

  public static void sendDeployAdd(ServerLevel level, StructureDeployment deployment) {
    RegistryFriendlyByteBuf buf = writeDeployAdd(deployment, level.registryAccess());
    NetworkManager.sendToPlayers(level.players(), DEPLOY_ADD, buf);
  }

  public static void sendDeployAdd(ServerPlayer player, StructureDeployment deployment) {
    RegistryFriendlyByteBuf buf = writeDeployAdd(deployment, player.registryAccess());
    NetworkManager.sendToPlayer(player, DEPLOY_ADD, buf);
  }

  public static void sendDeployRemove(ServerLevel level, UUID id) {
    RegistryFriendlyByteBuf buf =
        new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
    buf.writeUUID(id);
    NetworkManager.sendToPlayers(level.players(), DEPLOY_REMOVE, buf);
  }

  private static RegistryFriendlyByteBuf writeDeployAdd(
      StructureDeployment deployment, RegistryAccess registryAccess) {
    RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess);
    buf.writeUUID(deployment.id());
    buf.writeBlockPos(deployment.origin());
    buf.writeByte(deployment.facing().get2DDataValue());
    buf.writeLong(deployment.startGameTime());
    buf.writeVarInt(deployment.duration());
    buf.writeNbt(deployment.blueprint().save());
    return buf;
  }

  private static StructureDeployment readDeployAdd(RegistryFriendlyByteBuf buf) {
    UUID id = buf.readUUID();
    BlockPos origin = buf.readBlockPos();
    Direction facing = Direction.from2DDataValue(buf.readByte());
    long startGameTime = buf.readLong();
    int duration = buf.readVarInt();
    CompoundTag blueprintTag = buf.readNbt();
    StructureBlueprint blueprint =
        StructureBlueprint.load(
                blueprintTag != null ? blueprintTag : new CompoundTag(), buf.registryAccess())
            .orElseGet(() -> new StructureBlueprint(BlockPos.ZERO, java.util.List.of()));
    return new StructureDeployment(id, origin, facing, blueprint, startGameTime, duration);
  }
}
