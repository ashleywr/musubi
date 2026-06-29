package com.sanhiruzu.musubi.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

public class SilkwormBoilingCategory implements IRecipeCategory<SilkwormBoilingRecipe> {

    public static final RecipeType<SilkwormBoilingRecipe> RECIPE_TYPE =
        RecipeType.create("musubi", "silkworm_boiling", SilkwormBoilingRecipe.class);

    /** Layout:
     *  Row 0 (y=1):  [cocoon]  →  [silk_thread]
     *  Row 1 (y=23): [campfire]  "beneath cauldron"
     */
    private static final int W = 110, H = 42;
    private static final int SLOT_Y   = 1,  COCOON_X = 1,  SILK_X   = 55;
    private static final int ARROW_X  = 23, ARROW_Y  = 5;
    private static final int FIRE_X   = 1,  FIRE_Y   = 23;
    private static final int HINT_X   = 22, HINT_Y   = 28;

    private final IDrawable icon;

    public SilkwormBoilingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(Items.CAULDRON.getDefaultInstance());
    }

    @Override public RecipeType<SilkwormBoilingRecipe> getRecipeType() { return RECIPE_TYPE; }
    @Override public Component getTitle() { return Component.translatable("musubi.jei.silkworm_boiling"); }
    @Override public IDrawable getIcon()  { return icon; }
    @Override public int getWidth()       { return W; }
    @Override public int getHeight()      { return H; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SilkwormBoilingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT,       COCOON_X, SLOT_Y).addItemStack(recipe.input());
        builder.addSlot(RecipeIngredientRole.OUTPUT,      SILK_X,   SLOT_Y).addItemStack(recipe.output());
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, FIRE_X,   FIRE_Y).addItemStack(recipe.fire());
    }

    @Override
    public void draw(SilkwormBoilingRecipe recipe, IRecipeSlotsView slots,
                     GuiGraphics gfx, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        gfx.drawString(font, "→", ARROW_X, ARROW_Y, 0xFF555555, false);
        gfx.drawString(font,
            Component.translatable("musubi.jei.silkworm_boiling.fire_hint"),
            HINT_X, HINT_Y, 0xFF888888, false);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, SilkwormBoilingRecipe recipe,
                           IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseY >= FIRE_Y && mouseY < FIRE_Y + 18 && mouseX >= FIRE_X && mouseX < W) {
            tooltip.add(Component.translatable("musubi.jei.silkworm_boiling.fire_tooltip"));
        }
    }
}
