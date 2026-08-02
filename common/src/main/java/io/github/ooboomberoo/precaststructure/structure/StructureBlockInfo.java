package io.github.ooboomberoo.precaststructure.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public record StructureBlockInfo(BlockPos offset, BlockState state, @Nullable CompoundTag nbt) {
    public static final Codec<StructureBlockInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("x").forGetter(block -> block.offset().getX()),
        Codec.INT.fieldOf("y").forGetter(block -> block.offset().getY()),
        Codec.INT.fieldOf("z").forGetter(block -> block.offset().getZ()),
        BlockState.CODEC.fieldOf("state").forGetter(StructureBlockInfo::state),
        CompoundTag.CODEC.optionalFieldOf("nbt").forGetter(block -> Optional.ofNullable(block.nbt()))
    ).apply(instance, (x, y, z, state, nbt) -> new StructureBlockInfo(new BlockPos(x, y, z), state, nbt.orElse(null))));

    private static final StreamCodec<ByteBuf, CompoundTag> NULLABLE_NBT = ByteBufCodecs.OPTIONAL_COMPOUND_TAG.map(
        optional -> optional.orElse(null),
        nbt -> Optional.ofNullable(nbt)
    );

    public static final StreamCodec<ByteBuf, StructureBlockInfo> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        StructureBlockInfo::offset,
        ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY),
        StructureBlockInfo::state,
        NULLABLE_NBT,
        StructureBlockInfo::nbt,
        StructureBlockInfo::new
    );

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
