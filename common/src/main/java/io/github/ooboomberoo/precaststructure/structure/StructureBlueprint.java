package io.github.ooboomberoo.precaststructure.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.ooboomberoo.precaststructure.config.ModConfig;
import io.github.ooboomberoo.precaststructure.structure.special.SpecialBlockHandlers;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public record StructureBlueprint(BlockPos size, List<StructureBlockInfo> blocks) {
  public static final String ROOT_KEY = "PrecastStructure";

  /** Matches legacy {@link #save()} layout ({@code size} as `{x,y,z}` compound). */
  private static final Codec<BlockPos> SIZE_CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      Codec.INT.fieldOf("x").forGetter(BlockPos::getX),
                      Codec.INT.fieldOf("y").forGetter(BlockPos::getY),
                      Codec.INT.fieldOf("z").forGetter(BlockPos::getZ))
                  .apply(instance, BlockPos::new));

  public static final Codec<StructureBlueprint> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      SIZE_CODEC.fieldOf("size").forGetter(StructureBlueprint::size),
                      StructureBlockInfo.CODEC
                          .listOf()
                          .fieldOf("blocks")
                          .forGetter(StructureBlueprint::blocks))
                  .apply(
                      instance,
                      (size, blocks) ->
                          new StructureBlueprint(clampSize(size), List.copyOf(blocks))));

  public static final StreamCodec<ByteBuf, StructureBlueprint> STREAM_CODEC =
      StreamCodec.composite(
          BlockPos.STREAM_CODEC,
          StructureBlueprint::size,
          StructureBlockInfo.STREAM_CODEC.apply(ByteBufCodecs.list()),
          StructureBlueprint::blocks,
          (size, blocks) -> new StructureBlueprint(clampSize(size), List.copyOf(blocks)));

  private static BlockPos clampSize(BlockPos size) {
    int maxDimension = ModConfig.get().blueprint.maxDimension;
    return new BlockPos(
        Mth.clamp(size.getX(), 0, maxDimension),
        Mth.clamp(size.getY(), 0, maxDimension),
        Mth.clamp(size.getZ(), 0, maxDimension));
  }

  /**
   * Shrinks {@link #size()} to the occupied block AABB and rebases offsets to (0,0,0). Used when
   * persisting onto a blueprint item ({@code BlueprintItemData.write}); live scan / hologram data
   * keeps the full frame-relative capture until then. Empty blueprints become a zero-size empty
   * list.
   */
  public StructureBlueprint trimmedToContents() {
    if (blocks.isEmpty()) {
      return new StructureBlueprint(BlockPos.ZERO, List.of());
    }

    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;
    int maxZ = Integer.MIN_VALUE;
    for (StructureBlockInfo block : blocks) {
      BlockPos offset = block.offset();
      minX = Math.min(minX, offset.getX());
      minY = Math.min(minY, offset.getY());
      minZ = Math.min(minZ, offset.getZ());
      maxX = Math.max(maxX, offset.getX());
      maxY = Math.max(maxY, offset.getY());
      maxZ = Math.max(maxZ, offset.getZ());
    }

    BlockPos origin = new BlockPos(minX, minY, minZ);
    BlockPos trimmedSize = new BlockPos(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    if (origin.equals(BlockPos.ZERO) && trimmedSize.equals(size)) {
      return this;
    }

    List<StructureBlockInfo> shifted = new ArrayList<>(blocks.size());
    for (StructureBlockInfo block : blocks) {
      shifted.add(
          new StructureBlockInfo(block.offset().subtract(origin), block.state(), block.nbt()));
    }
    return new StructureBlueprint(trimmedSize, List.copyOf(shifted));
  }

  public CompoundTag save() {
    CompoundTag root = new CompoundTag();
    CompoundTag sizeTag = new CompoundTag();
    sizeTag.putInt("x", size.getX());
    sizeTag.putInt("y", size.getY());
    sizeTag.putInt("z", size.getZ());
    root.put("size", sizeTag);

    ListTag blockList = new ListTag();
    for (StructureBlockInfo block : blocks) {
      blockList.add(block.save());
    }
    root.put("blocks", blockList);
    return root;
  }

  public static Optional<StructureBlueprint> load(
      CompoundTag root, HolderLookup.Provider registries) {
    if (!root.contains("size", Tag.TAG_COMPOUND) || !root.contains("blocks", Tag.TAG_LIST)) {
      return Optional.empty();
    }

    CompoundTag sizeTag = root.getCompound("size");
    BlockPos size =
        clampSize(new BlockPos(sizeTag.getInt("x"), sizeTag.getInt("y"), sizeTag.getInt("z")));
    ListTag blockList = root.getList("blocks", Tag.TAG_COMPOUND);
    HolderGetter<Block> blockLookup = registries.lookupOrThrow(Registries.BLOCK);
    List<StructureBlockInfo> blocks = new ArrayList<>(blockList.size());
    for (Tag entry : blockList) {
      CompoundTag blockTag = (CompoundTag) entry;
      BlockPos offset =
          new BlockPos(blockTag.getInt("x"), blockTag.getInt("y"), blockTag.getInt("z"));
      BlockState state =
          net.minecraft.nbt.NbtUtils.readBlockState(blockLookup, blockTag.getCompound("state"));
      CompoundTag nbt =
          blockTag.contains("nbt", Tag.TAG_COMPOUND) ? blockTag.getCompound("nbt") : null;
      blocks.add(new StructureBlockInfo(offset, state, nbt));
    }
    return Optional.of(new StructureBlueprint(size, List.copyOf(blocks)));
  }

  public static Optional<StructureBlueprint> load(CompoundTag root) {
    return load(root, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
  }

  public Map<Item, Integer> requiredItems() {
    return requiredItems(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
  }

  public Map<Item, Integer> requiredItems(HolderLookup.Provider registries) {
    Map<Item, Integer> requirements = new LinkedHashMap<>();
    for (StructureBlockInfo block : blocks) {
      // Beds/doors/Create kinetics go through SpecialBlockHandlers (first match wins).
      // Create must not short-circuit vanilla multi-block halves before Bed/DoubleBlock handlers.
      if (!SpecialBlockHandlers.mergeRequirements(
          block.state(), block.nbt(), requirements, registries)) {
        Item item = block.state().getBlock().asItem();
        if (item != net.minecraft.world.item.Items.AIR) {
          requirements.merge(item, 1, Integer::sum);
        }
      }
    }
    return requirements;
  }

  /**
   * One input slot per unique item; splits into multiple slots when amount exceeds max stack size.
   */
  public List<MaterialRequirement> materialSlotRequirements() {
    return materialSlotRequirements(
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
  }

  public List<MaterialRequirement> materialSlotRequirements(HolderLookup.Provider registries) {
    List<MaterialRequirement> slots = new ArrayList<>();
    for (Map.Entry<Item, Integer> entry : requiredItems(registries).entrySet()) {
      Item item = entry.getKey();
      int remaining = entry.getValue();
      int maxStack = Math.max(1, item.getDefaultMaxStackSize());
      while (remaining > 0) {
        int amount = Math.min(remaining, maxStack);
        slots.add(new MaterialRequirement(item, amount));
        remaining -= amount;
      }
    }
    return slots;
  }
}
