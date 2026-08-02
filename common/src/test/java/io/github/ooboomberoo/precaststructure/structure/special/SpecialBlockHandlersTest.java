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
    void placementStripRemovesContainerItemsAndLootTables() {
        // Capture empties inventories in-world; InventoryNbt remains a placement safety net.
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

        CompoundTag sanitized = SpecialBlockHandlers.sanitizePlacement(
            Blocks.CHEST.defaultBlockState(),
            nbt
        );
        assertTrue(sanitized != null);
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
    void sanitizeCapturedLeavesNbtUnchanged() {
        // Inventories are emptied before serialize; capture sanitize must not key-strip.
        CompoundTag nbt = new CompoundTag();
        nbt.put("Items", new ListTag());
        CompoundTag stack = new CompoundTag();
        stack.putString("id", "minecraft:stone");
        nbt.getList("Items", 10).add(stack);
        nbt.putString("CustomName", "\"KeepMe\"");

        CompoundTag sanitized = SpecialBlockHandlers.sanitizeCaptured(
            Blocks.CHEST.defaultBlockState(),
            nbt
        );
        assertTrue(sanitized != null && sanitized.contains("Items"));
        assertEquals("\"KeepMe\"", sanitized.getString("CustomName"));
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

    @Test
    void lecternAndChestNeedNoSpecialHandler() {
        // Book/HAS_BOOK cleared in-world via ContainerCapture before serialize.
        var withBook = Blocks.LECTERN.defaultBlockState().setValue(
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HAS_BOOK, true
        );
        assertEquals(withBook, SpecialBlockHandlers.sanitizeCapturedState(withBook));
        assertNull(SpecialBlockHandlers.find(withBook));
        assertNull(SpecialBlockHandlers.find(Blocks.CREEPER_HEAD.defaultBlockState()));
        assertNull(SpecialBlockHandlers.find(Blocks.CHEST.defaultBlockState()));
        assertNull(SpecialBlockHandlers.find(Blocks.SHULKER_BOX.defaultBlockState()));
        assertNull(SpecialBlockHandlers.find(Blocks.ENDER_CHEST.defaultBlockState()));
    }

    @Test
    void signsNeedNoSpecialHandlerButKeepTextAndAreBerPrimary() {
        assertNull(SpecialBlockHandlers.find(Blocks.OAK_SIGN.defaultBlockState()));
        assertNull(SpecialBlockHandlers.find(Blocks.OAK_WALL_SIGN.defaultBlockState()));
        assertNull(SpecialBlockHandlers.find(Blocks.OAK_HANGING_SIGN.defaultBlockState()));
        assertNull(SpecialBlockHandlers.find(Blocks.OAK_WALL_HANGING_SIGN.defaultBlockState()));
        assertTrue(SpecialBlockHandlers.shouldRenderPreview(Blocks.OAK_SIGN.defaultBlockState()));
        // Signs/skulls use INVISIBLE or ENTITYBLOCK_ANIMATED — both are BER-primary.
        var berPrimary = java.util.EnumSet.of(
            net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED,
            net.minecraft.world.level.block.RenderShape.INVISIBLE
        );
        assertTrue(berPrimary.contains(Blocks.OAK_SIGN.defaultBlockState().getRenderShape()));
        assertTrue(berPrimary.contains(Blocks.CREEPER_HEAD.defaultBlockState().getRenderShape()));
        assertTrue(berPrimary.contains(Blocks.CHEST.defaultBlockState().getRenderShape()));
        assertFalse(berPrimary.contains(Blocks.LECTERN.defaultBlockState().getRenderShape()));
        assertEquals(
            net.minecraft.world.level.block.RenderShape.MODEL,
            Blocks.LECTERN.defaultBlockState().getRenderShape()
        );
    }

    @Test
    void sanitizeCapturedKeepsSignTextNbt() {
        CompoundTag nbt = new CompoundTag();
        CompoundTag front = new CompoundTag();
        ListTag messages = new ListTag();
        messages.add(net.minecraft.nbt.StringTag.valueOf("\"Hello\""));
        messages.add(net.minecraft.nbt.StringTag.valueOf("\"\""));
        messages.add(net.minecraft.nbt.StringTag.valueOf("\"\""));
        messages.add(net.minecraft.nbt.StringTag.valueOf("\"\""));
        front.put("messages", messages);
        front.putString("color", "black");
        front.putBoolean("has_glowing_text", false);
        nbt.put("front_text", front);
        nbt.putBoolean("is_waxed", true);

        CompoundTag sanitized = SpecialBlockHandlers.sanitizeCaptured(
            Blocks.OAK_SIGN.defaultBlockState(),
            nbt
        );
        assertTrue(sanitized != null && sanitized.contains("front_text"));
        assertTrue(sanitized.getBoolean("is_waxed"));
    }

    @Test
    void sanitizePlacementStripsLegacyItemsFromSignNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("is_waxed", true);
        nbt.put("Items", new ListTag());

        CompoundTag sanitized = SpecialBlockHandlers.sanitizePlacement(
            Blocks.OAK_SIGN.defaultBlockState(),
            nbt
        );
        assertTrue(sanitized != null && sanitized.getBoolean("is_waxed"));
        assertFalse(sanitized.contains("Items"));
    }

    @Test
    void transformNbtNoopsWithoutMatchingHandler() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("CustomName", "\"Keep\"");
        // No Create handler in unit tests — facade must leave NBT unchanged.
        assertEquals(
            nbt,
            SpecialBlockHandlers.transformNbt(
                Blocks.CHEST.defaultBlockState(),
                nbt,
                net.minecraft.world.level.block.Rotation.CLOCKWISE_90,
                net.minecraft.core.RegistryAccess.EMPTY
            )
        );
        assertEquals(
            nbt,
            SpecialBlockHandlers.transformNbt(
                Blocks.STONE.defaultBlockState(),
                nbt,
                net.minecraft.world.level.block.Rotation.NONE,
                net.minecraft.core.RegistryAccess.EMPTY
            )
        );
    }

    @Test
    void sanitizePlacementStripsLecternPageFromLegacyNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("Page", 3);
        nbt.putString("id", "minecraft:lectern");
        CompoundTag sanitized = SpecialBlockHandlers.sanitizePlacement(
            Blocks.LECTERN.defaultBlockState(),
            nbt
        );
        assertTrue(sanitized != null);
        assertFalse(sanitized.contains("Page"));
        assertEquals("minecraft:lectern", sanitized.getString("id"));
    }
}
