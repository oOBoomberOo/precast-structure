package io.github.ooboomberoo.precaststructure.structure;

import io.github.ooboomberoo.precaststructure.compat.CreateCompat;
import io.github.ooboomberoo.precaststructure.config.ModConfig;
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
        int maxDimension = ModConfig.get().blueprint.maxDimension;
        BlockPos size = new BlockPos(
            Mth.clamp(sizeTag.getInt("x"), 0, maxDimension),
            Mth.clamp(sizeTag.getInt("y"), 0, maxDimension),
            Mth.clamp(sizeTag.getInt("z"), 0, maxDimension)
        );
        ListTag blockList = root.getList("blocks", Tag.TAG_COMPOUND);
        HolderGetter<Block> blockLookup = registries.lookupOrThrow(Registries.BLOCK);
        List<StructureBlockInfo> blocks = new ArrayList<>(blockList.size());
        for (Tag entry : blockList) {
            CompoundTag blockTag = (CompoundTag) entry;
            BlockPos offset = new BlockPos(blockTag.getInt("x"), blockTag.getInt("y"), blockTag.getInt("z"));
            BlockState state = net.minecraft.nbt.NbtUtils.readBlockState(blockLookup, blockTag.getCompound("state"));
            CompoundTag nbt = blockTag.contains("nbt", Tag.TAG_COMPOUND) ? blockTag.getCompound("nbt") : null;
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
            if (!CreateCompat.tryMergeRequirements(block.state(), requirements)) {
                Item item = block.state().getBlock().asItem();
                if (item != net.minecraft.world.item.Items.AIR) {
                    requirements.merge(item, 1, Integer::sum);
                }
            }
            Item bracket = CreateCompat.bracketItem(block.nbt(), registries);
            if (bracket != null) {
                requirements.merge(bracket, 1, Integer::sum);
            }
        }
        return requirements;
    }

    /** One input slot per unique item; splits into multiple slots when amount exceeds max stack size. */
    public List<MaterialRequirement> materialSlotRequirements() {
        return materialSlotRequirements(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
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
