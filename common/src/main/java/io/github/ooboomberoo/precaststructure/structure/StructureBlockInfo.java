package io.github.ooboomberoo.precaststructure.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

public record StructureBlockInfo(BlockPos offset, BlockState state) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", offset.getX());
        tag.putInt("y", offset.getY());
        tag.putInt("z", offset.getZ());
        tag.put("state", net.minecraft.nbt.NbtUtils.writeBlockState(state));
        return tag;
    }
}
