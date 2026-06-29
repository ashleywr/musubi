package com.sanhiruzu.musubi.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.util.List;

@JeiPlugin
public class MusubiJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_ID =
        ResourceLocation.fromNamespaceAndPath("musubi", "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() { return PLUGIN_ID; }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
            new SilkwormBoilingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(SilkwormBoilingCategory.RECIPE_TYPE,
            List.of(SilkwormBoilingRecipe.INSTANCE));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
            Items.CAULDRON.getDefaultInstance(),
            SilkwormBoilingCategory.RECIPE_TYPE);
    }
}
