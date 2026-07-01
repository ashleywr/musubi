package com.sanhiruzu.musubi.compat.jei;

import com.sanhiruzu.musubi.block.MusubiBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.constants.RecipeTypes;
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
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
            new SilkwormBoilingCategory(gui),
            new SpinningWheelSpinningCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(SilkwormBoilingCategory.RECIPE_TYPE,
            List.of(SilkwormBoilingRecipe.INSTANCE));
        registration.addRecipes(SpinningWheelSpinningCategory.RECIPE_TYPE,
            List.of(SpinningWheelSpinningRecipe.INSTANCE));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
            Items.CAULDRON.getDefaultInstance(),
            SilkwormBoilingCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(
            MusubiBlocks.SPINNING_WHEEL.get().asItem().getDefaultInstance(),
            SpinningWheelSpinningCategory.RECIPE_TYPE);
        // The loom automates any crafting recipe — show it as a catalyst there.
        registration.addRecipeCatalyst(
            MusubiBlocks.LOOM.get().asItem().getDefaultInstance(),
            RecipeTypes.CRAFTING);
    }
}
