package io.github.ooboomberoo.precaststructure.item;

import io.github.ooboomberoo.precaststructure.structure.BlueprintItemData;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import io.github.ooboomberoo.precaststructure.structure.StructurePlacement;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class PrecastStructureItem extends Item {
    public PrecastStructureItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Optional<StructureBlueprint> optional = BlueprintItemData.read(context.getItemInHand(), level.registryAccess());
        if (optional.isEmpty()) {
            return InteractionResult.FAIL;
        }

        StructureBlueprint blueprint = optional.get();
        BlockPos origin = StructurePlacement.resolveOrigin(context);
        Direction facing = context.getHorizontalDirection();
        Optional<BlockPos> blocked = StructurePlacement.firstBlockedPosition(level, origin, blueprint, facing);
        if (blocked.isPresent()) {
            Player player = context.getPlayer();
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.precast_structure.blocked_placement", blocked.get().getX(), blocked.get().getY(), blocked.get().getZ()), true);
            }
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide()) {
            StructurePlacement.place(level, origin, blueprint, facing);
            Player player = context.getPlayer();
            if (player != null && !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        Optional<StructureBlueprint> optional = BlueprintItemData.read(stack, context.registries());
        if (optional.isPresent()) {
            StructureBlueprint blueprint = optional.get();
            tooltipComponents.add(Component.translatable("tooltip.precast_structure.placeable_structure", blueprint.size().getX(), blueprint.size().getY(), blueprint.size().getZ()).withStyle(ChatFormatting.GOLD));
            tooltipComponents.add(Component.translatable("tooltip.precast_structure.ghost_preview").withStyle(ChatFormatting.GRAY));
        }
    }
}
