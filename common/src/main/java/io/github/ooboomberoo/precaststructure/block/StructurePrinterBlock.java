package io.github.ooboomberoo.precaststructure.block;

import io.github.ooboomberoo.precaststructure.registry.ModItems;
import io.github.ooboomberoo.precaststructure.structure.BlueprintItemData;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class StructurePrinterBlock extends Block {
    public StructurePrinterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.translatable("message.precaststructure.printer_needs_blueprint"), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!stack.is(ModItems.BLUEPRINT.get())) {
            player.displayClientMessage(Component.translatable("message.precaststructure.printer_needs_blueprint"), true);
            return ItemInteractionResult.CONSUME;
        }

        Optional<StructureBlueprint> optional = BlueprintItemData.read(stack, level.registryAccess());
        if (optional.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.precaststructure.invalid_blueprint"), true);
            return ItemInteractionResult.CONSUME;
        }

        StructureBlueprint blueprint = optional.get();
        if (!hasMaterials(player.getInventory(), blueprint.requiredItems())) {
            player.displayClientMessage(Component.translatable("message.precaststructure.missing_materials"), true);
            return ItemInteractionResult.CONSUME;
        }

        if (!player.getAbilities().instabuild) {
            consumeMaterials(player.getInventory(), blueprint.requiredItems());
            stack.shrink(1);
        }

        ItemStack structureStack = new ItemStack(ModItems.PRECAST_STRUCTURE.get());
        BlueprintItemData.write(structureStack, blueprint);
        if (!player.addItem(structureStack)) {
            player.drop(structureStack, false);
        }
        player.displayClientMessage(Component.translatable("message.precaststructure.print_complete"), true);
        return ItemInteractionResult.CONSUME;
    }

    private static boolean hasMaterials(Inventory inventory, Map<Item, Integer> requiredItems) {
        for (Map.Entry<Item, Integer> entry : requiredItems.entrySet()) {
            if (inventory.countItem(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static void consumeMaterials(Inventory inventory, Map<Item, Integer> requiredItems) {
        for (Map.Entry<Item, Integer> entry : requiredItems.entrySet()) {
            int remaining = entry.getValue();
            for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.is(entry.getKey())) {
                    continue;
                }
                int consumed = Math.min(remaining, stack.getCount());
                stack.shrink(consumed);
                remaining -= consumed;
            }
        }
    }
}
