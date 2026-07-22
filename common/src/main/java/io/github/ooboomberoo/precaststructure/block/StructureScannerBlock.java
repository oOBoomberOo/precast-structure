package io.github.ooboomberoo.precaststructure.block;

import io.github.ooboomberoo.precaststructure.registry.ModItems;
import io.github.ooboomberoo.precaststructure.structure.BlueprintCapture;
import io.github.ooboomberoo.precaststructure.structure.BlueprintItemData;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import io.github.ooboomberoo.precaststructure.structure.StructureFrameDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class StructureScannerBlock extends Block {
    public StructureScannerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        StructureFrameDetector.ScanResult result = StructureFrameDetector.detect(level, pos);
        if (!result.successful()) {
            player.displayClientMessage(result.error(), true);
            return InteractionResult.CONSUME;
        }

        StructureBlueprint blueprint = BlueprintCapture.capture(level, result.frameOptional().orElseThrow());
        if (blueprint.blocks().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.precaststructure.empty_scan"), true);
            return InteractionResult.CONSUME;
        }

        ItemStack blueprintStack = new ItemStack(ModItems.BLUEPRINT.get());
        BlueprintItemData.write(blueprintStack, blueprint);
        if (!player.addItem(blueprintStack)) {
            player.drop(blueprintStack, false);
        }
        player.displayClientMessage(Component.translatable("message.precaststructure.scan_complete", blueprint.blocks().size()), true);
        return InteractionResult.CONSUME;
    }
}
