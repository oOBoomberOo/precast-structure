package io.github.ooboomberoo.precaststructure.registry;

import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModBlockTags {
    public static final TagKey<Block> STRUCTURE_REPLACEABLE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(PrecastStructureMod.MOD_ID, "structure_replaceable"));

    private ModBlockTags() {
    }
}
