package io.github.ooboomberoo.precaststructure.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public record StructureBlockInfo(BlockPos offset, BlockState state, @Nullable CompoundTag nbt) {
    public StructureBlockInfo(BlockPos offset, BlockState state) {
        this(offset, state, null);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", offset.getX());
        tag.putInt("y", offset.getY());
        tag.putInt("z", offset.getZ());
        tag.put("state", net.minecraft.nbt.NbtUtils.writeBlockState(state));
        if (nbt != null && !nbt.isEmpty()) {
            tag.put("nbt", nbt);
        }
        return tag;
    }
}
