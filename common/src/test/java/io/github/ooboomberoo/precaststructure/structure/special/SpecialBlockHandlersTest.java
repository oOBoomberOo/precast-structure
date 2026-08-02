package io.github.ooboomberoo.precaststructure.structure.special;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import io.github.ooboomberoo.precaststructure.structure.StructureBlockInfo;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SpecialBlockHandlersTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        SpecialBlockHandlers.bootstrap();
    }

    @Test
    void stripsContainerItemsAndLootTables() {
        CompoundTag nbt = new CompoundTag();
        ListTag items = new ListTag();
        CompoundTag stack = new CompoundTag();
        stack.putByte("Slot", (byte) 0);
        stack.putString("id", "minecraft:diamond");
        stack.putByte("count", (byte) 1);
        items.add(stack);
        nbt.put("Items", items);
        nbt.putString("LootTable", "minecraft:chests/simple_dungeon");
        nbt.putLong("LootTableSeed", 42L);
        nbt.putString("CustomName", "\"KeepMe\"");

        CompoundTag sanitized = InventoryNbt.stripContainerContents(nbt);
        assertFalse(sanitized.contains("Items"));
        assertFalse(sanitized.contains("LootTable"));
        assertFalse(sanitized.contains("LootTableSeed"));
        assertEquals("\"KeepMe\"", sanitized.getString("CustomName"));
    }

    @Test
    void bedFootCostsNothingButStillPreviews() {
        var head = Blocks.RED_BED.defaultBlockState().setValue(
            net.minecraft.world.level.block.BedBlock.PART, BedPart.HEAD
        );
        var foot = Blocks.RED_BED.defaultBlockState().setValue(
            net.minecraft.world.level.block.BedBlock.PART, BedPart.FOOT
        );

        assertEquals(OptionalInt.of(1), SpecialBlockHandlers.materialUnits(head));
        assertEquals(OptionalInt.of(0), SpecialBlockHandlers.materialUnits(foot));
        // Both halves need a hologram mesh: BedRenderer draws one piece per PART.
        assertTrue(SpecialBlockHandlers.shouldRenderPreview(head));
        assertTrue(SpecialBlockHandlers.shouldRenderPreview(foot));
    }

    @Test
    void blueprintRequiresOneBedForHeadAndFoot() {
        StructureBlueprint blueprint = bedBlueprint();

        assertEquals(1, blueprint.requiredItems().get(Blocks.RED_BED.asItem()));
        assertEquals(1, blueprint.requiredItems().size());
    }

    @Test
    void bedMaterialSlotsAreSingleRequirement() {
        var slots = bedBlueprint().materialSlotRequirements();
        assertEquals(1, slots.size());
        assertEquals(Blocks.RED_BED.asItem(), slots.get(0).item());
        assertEquals(1, slots.get(0).amount());
    }

    @Test
    void bedRequirementsSurviveSaveLoadRoundTrip() {
        StructureBlueprint loaded = StructureBlueprint.load(bedBlueprint().save()).orElseThrow();
        assertEquals(1, loaded.requiredItems().get(Blocks.RED_BED.asItem()));
        assertEquals(1, loaded.materialSlotRequirements().size());
        assertEquals(BedPart.FOOT, loaded.blocks().get(0).state().getValue(
            net.minecraft.world.level.block.BedBlock.PART
        ));
        assertEquals(BedPart.HEAD, loaded.blocks().get(1).state().getValue(
            net.minecraft.world.level.block.BedBlock.PART
        ));
    }

    private static StructureBlueprint bedBlueprint() {
        return new StructureBlueprint(
            new BlockPos(1, 1, 2),
            List.of(
                new StructureBlockInfo(
                    BlockPos.ZERO,
                    Blocks.RED_BED.defaultBlockState().setValue(
                        net.minecraft.world.level.block.BedBlock.PART, BedPart.FOOT
                    )
                ),
                new StructureBlockInfo(
                    new BlockPos(0, 0, 1),
                    Blocks.RED_BED.defaultBlockState().setValue(
                        net.minecraft.world.level.block.BedBlock.PART, BedPart.HEAD
                    )
                )
            )
        );
    }

    @Test
    void doorUpperHalfCostsNothing() {
        var lower = Blocks.OAK_DOOR.defaultBlockState().setValue(
            net.minecraft.world.level.block.DoorBlock.HALF, DoubleBlockHalf.LOWER
        );
        var upper = Blocks.OAK_DOOR.defaultBlockState().setValue(
            net.minecraft.world.level.block.DoorBlock.HALF, DoubleBlockHalf.UPPER
        );
        assertEquals(OptionalInt.of(1), SpecialBlockHandlers.materialUnits(lower));
        assertEquals(OptionalInt.of(0), SpecialBlockHandlers.materialUnits(upper));
    }

    @Test
    void sanitizeCapturedClearsChestItems() {
        CompoundTag nbt = new CompoundTag();
        nbt.put("Items", new ListTag());
        CompoundTag stack = new CompoundTag();
        stack.putString("id", "minecraft:stone");
        nbt.getList("Items", 10).add(stack);

        CompoundTag sanitized = SpecialBlockHandlers.sanitizeCaptured(
            Blocks.CHEST.defaultBlockState(),
            nbt
        );
        assertTrue(sanitized == null || !sanitized.contains("Items"));
    }

    @Test
    void emptyNbtStaysNull() {
        assertNull(SpecialBlockHandlers.sanitizeCaptured(Blocks.CHEST.defaultBlockState(), null));
    }

    @Test
    void mergeRequirementsUsesBedHandlerNotDefaultAsItem() {
        var requirements = new java.util.LinkedHashMap<net.minecraft.world.item.Item, Integer>();
        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
            net.minecraft.core.registries.BuiltInRegistries.REGISTRY
        );
        var head = Blocks.RED_BED.defaultBlockState().setValue(
            net.minecraft.world.level.block.BedBlock.PART, BedPart.HEAD
        );
        var foot = Blocks.RED_BED.defaultBlockState().setValue(
            net.minecraft.world.level.block.BedBlock.PART, BedPart.FOOT
        );

        assertTrue(SpecialBlockHandlers.mergeRequirements(head, null, requirements, registries));
        assertTrue(SpecialBlockHandlers.mergeRequirements(foot, null, requirements, registries));
        assertEquals(1, requirements.get(Blocks.RED_BED.asItem()));
    }

    @Test
    void createSpecialHandlerMatchesOnlyCreateNamespace() {
        var handler = new io.github.ooboomberoo.precaststructure.compat.CreateSpecialHandler();
        assertFalse(handler.matches(Blocks.RED_BED.defaultBlockState()));
        assertFalse(handler.matches(Blocks.CHEST.defaultBlockState()));
        assertFalse(handler.matches(Blocks.STONE.defaultBlockState()));
    }
}
