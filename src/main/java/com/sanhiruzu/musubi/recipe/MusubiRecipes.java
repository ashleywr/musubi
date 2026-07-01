package com.sanhiruzu.musubi.recipe;

import com.sanhiruzu.musubi.Musubi;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MusubiRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, Musubi.MOD_ID);

    /** Special crafting recipe: blank pattern + any item → pattern encoded for that item. */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<PatternEncodingRecipe>> PATTERN_ENCODING =
        SERIALIZERS.register("pattern_encoding",
            () -> new SimpleCraftingRecipeSerializer<>(PatternEncodingRecipe::new));
}
