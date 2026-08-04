package io.github.ooboomberoo.precaststructure.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Legacy custom_data migration helpers that do not require Architectury registry bootstrap. */
class BlueprintItemDataLegacyTest {
  @BeforeAll
  static void bootstrapMinecraft() {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
  }

  @Test
  void legacyCustomDataContainsStructureKey() {
    StructureBlueprint blueprint =
        new StructureBlueprint(
            new BlockPos(2, 1, 1),
            List.of(
                new StructureBlockInfo(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState()),
                new StructureBlockInfo(
                    new BlockPos(1, 0, 0), Blocks.OAK_PLANKS.defaultBlockState())));
    ItemStack stack = new ItemStack(Items.PAPER);
    CompoundTag root = new CompoundTag();
    root.put(StructureBlueprint.ROOT_KEY, blueprint.save());
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    assertTrue(data != null && data.copyTag().contains(StructureBlueprint.ROOT_KEY));
    StructureBlueprint loaded =
        StructureBlueprint.load(data.copyTag().getCompound(StructureBlueprint.ROOT_KEY))
            .orElseThrow();
    assertEquals(blueprint.size(), loaded.size());
    assertEquals(2, loaded.blocks().size());
  }

  @Test
  void strippingRootKeyLeavesOtherCustomData() {
    CompoundTag root = new CompoundTag();
    root.put(StructureBlueprint.ROOT_KEY, new StructureBlueprint(BlockPos.ZERO, List.of()).save());
    root.putString("OtherModKey", "keep-me");
    root.remove(StructureBlueprint.ROOT_KEY);
    assertFalse(root.contains(StructureBlueprint.ROOT_KEY));
    assertEquals("keep-me", root.getString("OtherModKey"));
  }
}
