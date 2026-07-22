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
    public BlueprintItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        HolderLookup.Provider registries = context.registries();
        Optional<StructureBlueprint> optional = BlueprintItemData.read(stack, registries);
        if (optional.isEmpty()) {
            tooltipComponents.add(Component.translatable("tooltip.precaststructure.empty_blueprint").withStyle(ChatFormatting.GRAY));
            return;
        }

        StructureBlueprint blueprint = optional.get();
        tooltipComponents.add(Component.translatable("tooltip.precaststructure.dimensions", blueprint.size().getX(), blueprint.size().getY(), blueprint.size().getZ()).withStyle(ChatFormatting.AQUA));
        for (Map.Entry<net.minecraft.world.item.Item, Integer> entry : blueprint.requiredItems().entrySet()) {
            tooltipComponents.add(Component.literal("• " + entry.getValue() + " × ").append(entry.getKey().getDescription()).withStyle(ChatFormatting.GRAY));
        }
    }
}
