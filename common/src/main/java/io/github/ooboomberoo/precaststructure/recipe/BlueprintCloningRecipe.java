package io.github.ooboomberoo.precaststructure.recipe;

import io.github.ooboomberoo.precaststructure.registry.ModItems;
import io.github.ooboomberoo.precaststructure.registry.ModRecipeSerializers;
import io.github.ooboomberoo.precaststructure.structure.BlueprintItemData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Filled blueprint + empty blueprint → two filled blueprints (map-cloning style).
 */
public class BlueprintCloningRecipe extends CustomRecipe {
    public BlueprintCloningRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack filled = ItemStack.EMPTY;
        ItemStack empty = ItemStack.EMPTY;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.BLUEPRINT.get()) && BlueprintItemData.hasStructure(stack)) {
                if (!filled.isEmpty()) {
                    return false;
                }
                filled = stack;
            } else if (stack.is(ModItems.EMPTY_BLUEPRINT.get())) {
                if (!empty.isEmpty()) {
                    return false;
                }
                empty = stack;
            } else {
                return false;
            }
        }

        return !filled.isEmpty() && !empty.isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack filled = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.is(ModItems.BLUEPRINT.get()) && BlueprintItemData.hasStructure(stack)) {
                filled = stack;
                break;
            }
        }
        if (filled.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack result = filled.copy();
        result.setCount(2);
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.BLUEPRINT_CLONING.get();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }
}
