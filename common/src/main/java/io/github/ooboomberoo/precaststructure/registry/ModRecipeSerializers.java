package io.github.ooboomberoo.precaststructure.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import io.github.ooboomberoo.precaststructure.PrecastStructureMod;
import io.github.ooboomberoo.precaststructure.recipe.BlueprintCloningRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

public final class ModRecipeSerializers {
  public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
      DeferredRegister.create(PrecastStructureMod.MOD_ID, Registries.RECIPE_SERIALIZER);

  public static final RegistrySupplier<RecipeSerializer<BlueprintCloningRecipe>> BLUEPRINT_CLONING =
      RECIPE_SERIALIZERS.register(
          "blueprint_cloning",
          () -> new SimpleCraftingRecipeSerializer<>(BlueprintCloningRecipe::new));

  private ModRecipeSerializers() {}

  public static void register() {
    RECIPE_SERIALIZERS.register();
  }
}
