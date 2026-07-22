package io.github.ooboomberoo.precaststructure.structure;

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
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public record StructureBlueprint(BlockPos size, List<StructureBlockInfo> blocks) {
    public static final String ROOT_KEY = "PrecastStructure";

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

    public static Optional<StructureBlueprint> load(CompoundTag root, HolderLookup.Provider registries) {
        if (!root.contains("size", Tag.TAG_COMPOUND) || !root.contains("blocks", Tag.TAG_LIST)) {
            return Optional.empty();
        }

        CompoundTag sizeTag = root.getCompound("size");
        BlockPos size = new BlockPos(Mth.clamp(sizeTag.getInt("x"), 0, 256), Mth.clamp(sizeTag.getInt("y"), 0, 256), Mth.clamp(sizeTag.getInt("z"), 0, 256));
        ListTag blockList = root.getList("blocks", Tag.TAG_COMPOUND);
        HolderGetter<Block> blockLookup = registries.lookupOrThrow(Registries.BLOCK);
        List<StructureBlockInfo> blocks = new ArrayList<>(blockList.size());
        for (Tag entry : blockList) {
            CompoundTag blockTag = (CompoundTag) entry;
            BlockPos offset = new BlockPos(blockTag.getInt("x"), blockTag.getInt("y"), blockTag.getInt("z"));
            BlockState state = net.minecraft.nbt.NbtUtils.readBlockState(blockLookup, blockTag.getCompound("state"));
            blocks.add(new StructureBlockInfo(offset, state));
        }
        return Optional.of(new StructureBlueprint(size, List.copyOf(blocks)));
    }

    public static Optional<StructureBlueprint> load(CompoundTag root) {
        return load(root, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    public Map<Item, Integer> requiredItems() {
        Map<Item, Integer> requirements = new LinkedHashMap<>();
        for (StructureBlockInfo block : blocks) {
            Item item = block.state().getBlock().asItem();
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            requirements.merge(item, 1, Integer::sum);
        }
        return requirements;
    }
}
