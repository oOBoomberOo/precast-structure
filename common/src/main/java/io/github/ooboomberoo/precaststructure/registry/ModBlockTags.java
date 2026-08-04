package io.github.ooboomberoo.precaststructure.registry;

import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ModBlockTags {
  public static final TagKey<Block> STRUCTURE_REPLACEABLE =
      TagKey.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(
              PrecastStructureMod.MOD_ID, "structure_replaceable"));

  /**
   * Blocks skipped when capturing a blueprint (and left in place when scanning clears the frame).
   */
  public static final TagKey<Block> BLUEPRINT_EXCLUDED =
      TagKey.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath(PrecastStructureMod.MOD_ID, "blueprint_excluded"));

  private ModBlockTags() {}

  public static boolean isBlueprintExcluded(BlockState state) {
    return state.isAir() || state.is(BLUEPRINT_EXCLUDED);
  }
}
