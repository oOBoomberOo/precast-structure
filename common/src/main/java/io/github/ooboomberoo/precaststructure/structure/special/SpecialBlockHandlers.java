package io.github.ooboomberoo.precaststructure.structure.special;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Ordered registry of {@link SpecialBlockHandler}s. First match wins.
 *
 * <p>Built-ins cover beds (materials) and vertical double blocks (doors / tall plants). BER-primary
 * blocks (chests, signs, skulls, banners, …) use the generic preview path in {@code
 * HologramRenderSystem} — no per-block render handler required. Soft-compat modules (e.g. Create)
 * append with {@link #register} from mod init when present.
 *
 * <p>Capture empties inventories in-world before serialize; placement strips legacy item/loot keys
 * via {@link InventoryNbt} as a safety net for older blueprints.
 */
public final class SpecialBlockHandlers {
  private static final List<SpecialBlockHandler> HANDLERS = new ArrayList<>();
  private static boolean bootstrapped;

  static {
    bootstrap();
  }

  private SpecialBlockHandlers() {}

  public static synchronized void bootstrap() {
    if (bootstrapped) {
      return;
    }
    bootstrapped = true;
    register(new BedSpecialHandler());
    register(new DoubleBlockSpecialHandler());
  }

  /** Append a handler. Earlier registrations take priority on {@link #find}. */
  public static synchronized void register(SpecialBlockHandler handler) {
    HANDLERS.add(handler);
  }

  @Nullable
  public static SpecialBlockHandler find(BlockState state) {
    for (SpecialBlockHandler handler : HANDLERS) {
      if (handler.matches(state)) {
        return handler;
      }
    }
    return null;
  }

  /** Capture BE NBT via the matching handler, or vanilla save when none matches / Create absent. */
  @Nullable
  public static CompoundTag captureBlockEntityNbt(Level level, BlockEntity blockEntity) {
    SpecialBlockHandler handler = find(blockEntity.getBlockState());
    return handler != null
        ? handler.captureBlockEntityNbt(level, blockEntity)
        : saveBlockEntityNbt(level, blockEntity);
  }

  /** Apply BE NBT via the matching handler, or vanilla load when none matches / Create absent. */
  public static void applyBlockEntityNbt(
      Level level, BlockEntity blockEntity, BlockState placedState, @Nullable CompoundTag nbt) {
    SpecialBlockHandler handler = find(blockEntity.getBlockState());
    if (handler != null) {
      handler.applyBlockEntityNbt(level, blockEntity, placedState, nbt);
    } else {
      loadBlockEntityNbt(level, blockEntity, placedState, nbt);
    }
  }

  /** Vanilla {@code saveWithFullMetadata} with absolute coords stripped. */
  @Nullable
  public static CompoundTag saveBlockEntityNbt(Level level, BlockEntity blockEntity) {
    CompoundTag nbt = blockEntity.saveWithFullMetadata(level.registryAccess());
    nbt.remove("x");
    nbt.remove("y");
    nbt.remove("z");
    return nbt.isEmpty() ? null : nbt;
  }

  /** Vanilla {@code loadWithComponents} plus neighbour / client update. */
  public static void loadBlockEntityNbt(
      Level level, BlockEntity blockEntity, BlockState placedState, @Nullable CompoundTag nbt) {
    if (nbt == null || nbt.isEmpty()) {
      return;
    }
    CompoundTag tag = nbt.copy();
    tag.putInt("x", blockEntity.getBlockPos().getX());
    tag.putInt("y", blockEntity.getBlockPos().getY());
    tag.putInt("z", blockEntity.getBlockPos().getZ());
    blockEntity.loadWithComponents(tag, level.registryAccess());
    blockEntity.setChanged();
    if (!level.isClientSide()) {
      placedState.updateNeighbourShapes(level, blockEntity.getBlockPos(), 3);
      level.sendBlockUpdated(blockEntity.getBlockPos(), placedState, placedState, 3);
    }
  }

  @Nullable
  public static CompoundTag sanitizeCaptured(BlockState state, @Nullable CompoundTag nbt) {
    if (nbt == null || nbt.isEmpty()) {
      return nbt;
    }
    SpecialBlockHandler handler = find(state);
    CompoundTag sanitized = handler != null ? handler.sanitizeCapturedNbt(state, nbt) : nbt;
    return sanitized == null || sanitized.isEmpty() ? null : sanitized;
  }

  public static BlockState sanitizeCapturedState(BlockState state) {
    SpecialBlockHandler handler = find(state);
    return handler != null ? handler.sanitizeCapturedState(state) : state;
  }

  @Nullable
  public static CompoundTag sanitizePlacement(BlockState state, @Nullable CompoundTag nbt) {
    if (nbt == null || nbt.isEmpty()) {
      return nbt;
    }
    SpecialBlockHandler handler = find(state);
    CompoundTag sanitized = handler != null ? handler.sanitizePlacementNbt(state, nbt) : nbt;
    // Legacy blueprints may still embed item lists; strip on place as a safety net.
    sanitized = InventoryNbt.stripContainerContents(sanitized);
    return sanitized == null || sanitized.isEmpty() ? null : sanitized;
  }

  public static BlockState sanitizePlacementState(BlockState state) {
    SpecialBlockHandler handler = find(state);
    return handler != null ? handler.sanitizePlacementState(state) : state;
  }

  /**
   * Rotate nested BE-NBT block states for the matching handler (Create brackets, …). No-ops when no
   * handler matches or rotation is {@link Rotation#NONE}.
   */
  @Nullable
  public static CompoundTag transformNbt(
      BlockState state,
      @Nullable CompoundTag nbt,
      Rotation rotation,
      HolderLookup.Provider registries) {
    if (nbt == null || nbt.isEmpty() || rotation == Rotation.NONE) {
      return nbt;
    }
    SpecialBlockHandler handler = find(state);
    return handler != null ? handler.transformNbt(state, nbt, rotation, registries) : nbt;
  }

  public static OptionalInt materialUnits(BlockState state) {
    SpecialBlockHandler handler = find(state);
    return handler != null ? handler.materialUnits(state) : OptionalInt.empty();
  }

  /**
   * @return {@code true} if a special handler fully determined material costs for this block
   */
  public static boolean mergeRequirements(
      BlockState state,
      @Nullable CompoundTag nbt,
      Map<Item, Integer> requirements,
      HolderLookup.Provider registries) {
    SpecialBlockHandler handler = find(state);
    return handler != null && handler.mergeRequirements(state, nbt, requirements, registries);
  }

  public static boolean shouldRenderPreview(BlockState state) {
    SpecialBlockHandler handler = find(state);
    return handler == null || handler.shouldRenderPreview(state);
  }
}
