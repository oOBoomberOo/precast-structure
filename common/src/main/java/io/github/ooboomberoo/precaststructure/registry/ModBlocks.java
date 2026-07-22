package io.github.ooboomberoo.precaststructure.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.block.StructurePrinterBlock;
import io.github.ooboomberoo.precaststructure.block.StructureScannerBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(PrecastStructureMod.MOD_ID, Registries.BLOCK);

    public static final RegistrySupplier<Block> PLATFORM_FLOOR = BLOCKS.register("platform_floor", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistrySupplier<Block> PERIMETER_FENCE = BLOCKS.register("perimeter_fence", () -> new FenceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD)));
    public static final RegistrySupplier<Block> METAL_SCAFFOLD = BLOCKS.register("metal_scaffold", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistrySupplier<Block> STRUCTURE_SCANNER = BLOCKS.register("structure_scanner", () -> new StructureScannerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final RegistrySupplier<Block> STRUCTURE_PRINTER = BLOCKS.register("structure_printer", () -> new StructurePrinterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops()));

    private ModBlocks() {
    }

    public static void register() {
        BLOCKS.register();
    }
}
