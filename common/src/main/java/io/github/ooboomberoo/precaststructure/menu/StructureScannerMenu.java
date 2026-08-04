package io.github.ooboomberoo.precaststructure.menu;

import io.github.ooboomberoo.precaststructure.block.entity.StructureScannerBlockEntity;
import io.github.ooboomberoo.precaststructure.registry.ModBlocks;
import io.github.ooboomberoo.precaststructure.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class StructureScannerMenu extends AbstractContainerMenu {
  private final ContainerLevelAccess access;
  private final BlockPos blockPos;
  private final String initialStructureName;
  private final boolean plotStoragePos;
  private final @Nullable StructureScannerBlockEntity scanner;

  public StructureScannerMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
    this(
        containerId,
        inventory,
        buf.readBlockPos(),
        buf.readUtf(StructureScannerBlockEntity.MAX_NAME_LENGTH),
        null);
  }

  public StructureScannerMenu(
      int containerId, Inventory inventory, StructureScannerBlockEntity scanner) {
    this(containerId, inventory, scanner.getBlockPos(), scanner.getStructureName(), scanner);
  }

  public StructureScannerMenu(
      int containerId, Inventory inventory, BlockPos blockPos, String initialStructureName) {
    this(containerId, inventory, blockPos, initialStructureName, null);
  }

  private StructureScannerMenu(
      int containerId,
      Inventory inventory,
      BlockPos blockPos,
      String initialStructureName,
      @Nullable StructureScannerBlockEntity scanner) {
    super(ModMenuTypes.STRUCTURE_SCANNER.get(), containerId);
    this.blockPos = blockPos;
    this.initialStructureName = initialStructureName;
    this.scanner = scanner;
    this.plotStoragePos = isPlotStoragePos(blockPos);
    Level level = scanner != null ? scanner.getLevel() : inventory.player.level();
    this.access =
        level != null ? ContainerLevelAccess.create(level, blockPos) : ContainerLevelAccess.NULL;
  }

  public BlockPos getBlockPos() {
    return blockPos;
  }

  public String getInitialStructureName() {
    return initialStructureName;
  }

  @Nullable
  public StructureScannerBlockEntity getScanner() {
    if (scanner != null && !scanner.isRemoved()) {
      return scanner;
    }
    return access.evaluate(
        (level, pos) ->
            level.getBlockEntity(pos) instanceof StructureScannerBlockEntity found ? found : null,
        null);
  }

  private static boolean isPlotStoragePos(BlockPos pos) {
    // Sable plot storage sits far from the origin; vanilla stillValid distance fails there.
    return Math.abs(pos.getX()) > 1_000_000 || Math.abs(pos.getZ()) > 1_000_000;
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    return ItemStack.EMPTY;
  }

  @Override
  public boolean stillValid(Player player) {
    if (plotStoragePos) {
      return player.isAlive() && !player.isSpectator();
    }
    return stillValid(access, player, ModBlocks.STRUCTURE_SCANNER.get());
  }
}
