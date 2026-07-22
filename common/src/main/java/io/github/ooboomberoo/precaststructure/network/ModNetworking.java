package io.github.ooboomberoo.precaststructure.network;

import dev.architectury.networking.NetworkManager;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.block.entity.StructureScannerBlockEntity;
import io.github.ooboomberoo.precaststructure.menu.StructureScannerMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class ModNetworking {
    public static final ResourceLocation SCANNER_ACTION = ResourceLocation.fromNamespaceAndPath(PrecastStructureMod.MOD_ID, "scanner_action");

    private ModNetworking() {
    }

    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.clientToServer(), SCANNER_ACTION, (buf, context) -> {
            BlockPos pos = buf.readBlockPos();
            String structureName = buf.readUtf(StructureScannerBlockEntity.MAX_NAME_LENGTH);
            context.queue(() -> {
                if (!(context.getPlayer() instanceof ServerPlayer serverPlayer)) {
                    return;
                }
                if (!(serverPlayer.containerMenu instanceof StructureScannerMenu menu) || !menu.getBlockPos().equals(pos)) {
                    return;
                }
                if (!(serverPlayer.level().getBlockEntity(pos) instanceof StructureScannerBlockEntity scanner)) {
                    return;
                }
                scanner.setStructureName(structureName);
                scanner.scanStructure(serverPlayer);
            });
        });
    }

    public static void sendScannerAction(BlockPos pos, String structureName) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeUtf(structureName, StructureScannerBlockEntity.MAX_NAME_LENGTH);
        NetworkManager.sendToServer(SCANNER_ACTION, buf);
    }
}
