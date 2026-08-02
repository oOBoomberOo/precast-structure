package io.github.ooboomberoo.precaststructure.item;

import io.github.ooboomberoo.precaststructure.structure.BlueprintItemData;
import io.github.ooboomberoo.precaststructure.structure.StructureBlueprint;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class BlueprintItem extends Item {
    private static final int MAX_TOOLTIP_MATERIALS = 12;

    public BlueprintItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        HolderLookup.Provider registries = context.registries();
        Optional<StructureBlueprint> optional = BlueprintItemData.read(stack, registries);
        if (optional.isEmpty()) {
            tooltipComponents.add(Component.translatable("tooltip.precast_structure.empty_blueprint").withStyle(ChatFormatting.GRAY));
            return;
        }

        StructureBlueprint blueprint = optional.get();
        tooltipComponents.add(Component.translatable("tooltip.precast_structure.dimensions", blueprint.size().getX(), blueprint.size().getY(), blueprint.size().getZ()).withStyle(ChatFormatting.AQUA));

        int shown = 0;
        int total = blueprint.requiredItems(registries).size();
        for (Map.Entry<net.minecraft.world.item.Item, Integer> entry : blueprint.requiredItems(registries).entrySet()) {
            if (shown >= MAX_TOOLTIP_MATERIALS) {
                tooltipComponents.add(Component.translatable("tooltip.precast_structure.more_materials", total - shown).withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            tooltipComponents.add(Component.literal("• " + entry.getValue() + " × ").append(entry.getKey().getDescription()).withStyle(ChatFormatting.GRAY));
            shown++;
        }
    }
}
