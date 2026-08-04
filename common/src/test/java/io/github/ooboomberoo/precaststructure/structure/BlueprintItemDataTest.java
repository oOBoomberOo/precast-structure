package io.github.ooboomberoo.precaststructure.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Typed blueprint component / codec checks that do not require Architectury registry bootstrap. */
class BlueprintItemDataTest {
  private static final DataComponentType<StructureBlueprint> BLUEPRINT_STRUCTURE =
      DataComponentType.<StructureBlueprint>builder()
          .persistent(StructureBlueprint.CODEC)
          .networkSynchronized(StructureBlueprint.STREAM_CODEC)
          .build();

  @BeforeAll
  static void bootstrapMinecraft() {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
  }

  @Test
  void typedComponentStoresStructureBlueprintDirectly() {
    StructureBlueprint blueprint =
        new StructureBlueprint(
            new BlockPos(1, 2, 1),
            List.of(new StructureBlockInfo(BlockPos.ZERO, Blocks.STONE.defaultBlockState())));
    ItemStack stack = new ItemStack(Items.PAPER);
    stack.set(BLUEPRINT_STRUCTURE, blueprint);

    assertEquals(blueprint, stack.get(BLUEPRINT_STRUCTURE));
    CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
    assertTrue(custom == null || !custom.copyTag().contains(StructureBlueprint.ROOT_KEY));
  }

  @Test
  void writePersistsTrimmedRebasedBlueprintAndContentSizeName() {
    // Mimics untrimmed scan capture; BlueprintItemData.write stores trimmedToContents().
    // (Cannot call write() here — ModDataComponents needs Architectury registry bootstrap.)
    StructureBlueprint captured =
        new StructureBlueprint(
            new BlockPos(5, 4, 5),
            List.of(
                new StructureBlockInfo(new BlockPos(2, 1, 3), Blocks.STONE.defaultBlockState()),
                new StructureBlockInfo(new BlockPos(2, 2, 3), Blocks.STONE.defaultBlockState())));
    StructureBlueprint stored = captured.trimmedToContents();
    ItemStack stack = new ItemStack(Items.PAPER);
    stack.set(BLUEPRINT_STRUCTURE, stored);

    StructureBlueprint loaded = stack.get(BLUEPRINT_STRUCTURE);
    assertEquals(new BlockPos(1, 2, 1), loaded.size());
    assertEquals(2, loaded.blocks().size());
    assertEquals(BlockPos.ZERO, loaded.blocks().get(0).offset());
    assertEquals(new BlockPos(0, 1, 0), loaded.blocks().get(1).offset());
    // Default item name uses the same content dims as the stored (trimmed) size.
    assertEquals(new BlockPos(1, 2, 1), stored.size());
    // Idempotent: already-trimmed blueprints are not shifted again on a second write.
    assertEquals(stored, stored.trimmedToContents());
  }

  @Test
  void codecRoundTripsAndMatchesLegacySaveLayout() {
    StructureBlueprint blueprint =
        new StructureBlueprint(
            new BlockPos(2, 1, 1),
            List.of(
                new StructureBlockInfo(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState()),
                new StructureBlockInfo(
                    new BlockPos(1, 0, 0), Blocks.OAK_PLANKS.defaultBlockState())));

    Tag encoded = StructureBlueprint.CODEC.encodeStart(NbtOps.INSTANCE, blueprint).getOrThrow();
    assertTrue(
        encoded instanceof CompoundTag compound
            && compound.contains("size", Tag.TAG_COMPOUND)
            && compound.contains("blocks", Tag.TAG_LIST));

    StructureBlueprint fromCodec =
        StructureBlueprint.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
    assertEquals(blueprint.size(), fromCodec.size());
    assertEquals(blueprint.blocks().size(), fromCodec.blocks().size());
    assertEquals(Blocks.OAK_PLANKS, fromCodec.blocks().getFirst().state().getBlock());

    // Existing CompoundTag components used StructureBlueprint.save(); codec must read that shape.
    StructureBlueprint fromLegacySave =
        StructureBlueprint.CODEC.parse(NbtOps.INSTANCE, blueprint.save()).getOrThrow();
    assertEquals(blueprint.size(), fromLegacySave.size());
    assertEquals(2, fromLegacySave.blocks().size());
  }

  @Test
  void legacyCustomDataStillLoadsViaStructureBlueprint() {
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

    Optional<StructureBlueprint> loaded =
        StructureBlueprint.load(
            stack
                .get(DataComponents.CUSTOM_DATA)
                .copyTag()
                .getCompound(StructureBlueprint.ROOT_KEY));
    assertTrue(loaded.isPresent());
    assertEquals(blueprint.size(), loaded.get().size());

    // Migrated form: typed component, no PrecastStructure under custom_data.
    stack.set(BLUEPRINT_STRUCTURE, loaded.get());
    CompoundTag custom = stack.get(DataComponents.CUSTOM_DATA).copyTag();
    custom.remove(StructureBlueprint.ROOT_KEY);
    if (custom.isEmpty()) {
      stack.remove(DataComponents.CUSTOM_DATA);
    } else {
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }
    assertEquals(blueprint.size(), stack.get(BLUEPRINT_STRUCTURE).size());
    assertTrue(
        stack.get(DataComponents.CUSTOM_DATA) == null
            || !stack
                .get(DataComponents.CUSTOM_DATA)
                .copyTag()
                .contains(StructureBlueprint.ROOT_KEY));
  }
}
