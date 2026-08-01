package io.github.ooboomberoo.precaststructure.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.block.HologramColliderBlock;
import io.github.ooboomberoo.precaststructure.block.PerimeterFenceBlock;
import io.github.ooboomberoo.precaststructure.block.PerimeterFenceGateBlock;
import io.github.ooboomberoo.precaststructure.block.PlatformFloorBlock;
import io.github.ooboomberoo.precaststructure.block.StructureFrameBlock;
import io.github.ooboomberoo.precaststructure.block.StructurePrinterBlock;
import io.github.ooboomberoo.precaststructure.block.StructureScannerBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(PrecastStructureMod.MOD_ID, Registries.BLOCK);

    public static final RegistrySupplier<Block> PLATFORM_FLOOR = BLOCKS.register("platform_floor", () -> new PlatformFloorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().isValidSpawn((state, level, pos, type) -> false)));
    public static final RegistrySupplier<Block> PERIMETER_FENCE = BLOCKS.register("perimeter_fence", () -> new PerimeterFenceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD)));
    public static final RegistrySupplier<Block> PERIMETER_FENCE_GATE = BLOCKS.register("perimeter_fence_gate", () -> new PerimeterFenceGateBlock(WoodType.OAK, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).forceSolidOn().sound(SoundType.WOOD)));
    public static final RegistrySupplier<Block> METAL_SCAFFOLD = BLOCKS.register("metal_scaffold", () -> new StructureFrameBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion().isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false)));
    public static final RegistrySupplier<Block> STRUCTURE_SCANNER = BLOCKS.register("structure_scanner", () -> new StructureScannerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistrySupplier<Block> STRUCTURE_PRINTER = BLOCKS.register("structure_printer", () -> new StructurePrinterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    /** Invisible collision stand-in for scan/deploy holograms. Not in the creative tab. */
    public static final RegistrySupplier<Block> HOLOGRAM_COLLIDER = BLOCKS.register(
        "hologram_collider",
        () -> new HologramColliderBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .strength(-1.0F, 3600000.8F)
                .noLootTable()
                .noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .pushReaction(PushReaction.BLOCK)
                .instrument(NoteBlockInstrument.HAT)
        )
    );

    private ModBlocks() {
    }

    public static void register() {
        BLOCKS.register();
    }
}
