package com.sanhiruzu.musubi.compat.jei;

import com.sanhiruzu.musubi.block.MusubiBlocks;
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

public class SpinningWheelSpinningCategory implements IRecipeCategory<SpinningWheelSpinningRecipe> {

    public static final RecipeType<SpinningWheelSpinningRecipe> RECIPE_TYPE =
        RecipeType.create("musubi", "spinning_wheel_spinning", SpinningWheelSpinningRecipe.class);

    /** Layout:
     *  Row 0 (y=1):  [silk_thread]  →  [magical_silk_thread]
     *  Row 1 (y=23): [spinning_wheel]  "40 ticks per thread"
     */
    private static final int W = 110, H = 42;
    private static final int SLOT_Y   = 1, INPUT_X = 1, OUTPUT_X = 55;
    private static final int ARROW_X  = 23, ARROW_Y = 5;
    private static final int WHEEL_X  = 1,  WHEEL_Y = 23;
    private static final int HINT_X   = 22, HINT_Y  = 28;

    private final IDrawable icon;

    public SpinningWheelSpinningCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(MusubiBlocks.SPINNING_WHEEL.get().asItem().getDefaultInstance());
    }

    @Override public RecipeType<SpinningWheelSpinningRecipe> getRecipeType() { return RECIPE_TYPE; }
    @Override public Component getTitle() { return Component.translatable("musubi.jei.spinning_wheel_spinning"); }
    @Override public IDrawable getIcon()  { return icon; }
    @Override public int getWidth()       { return W; }
    @Override public int getHeight()      { return H; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, SpinningWheelSpinningRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT,       INPUT_X,  SLOT_Y).addItemStack(recipe.input());
        builder.addSlot(RecipeIngredientRole.OUTPUT,      OUTPUT_X, SLOT_Y).addItemStack(recipe.output());
        builder.addSlot(RecipeIngredientRole.RENDER_ONLY, WHEEL_X,  WHEEL_Y)
            .addItemStack(MusubiBlocks.SPINNING_WHEEL.get().asItem().getDefaultInstance());
    }

    @Override
    public void draw(SpinningWheelSpinningRecipe recipe, IRecipeSlotsView slots,
                     GuiGraphics gfx, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        gfx.drawString(font, "→", ARROW_X, ARROW_Y, 0xFF555555, false);
        gfx.drawString(font,
            Component.translatable("musubi.jei.spinning_wheel_spinning.hint"),
            HINT_X, HINT_Y, 0xFF888888, false);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, SpinningWheelSpinningRecipe recipe,
                           IRecipeSlotsView slots, double mouseX, double mouseY) {
        if (mouseY >= WHEEL_Y && mouseY < WHEEL_Y + 18 && mouseX >= WHEEL_X && mouseX < W) {
            tooltip.add(Component.translatable("musubi.jei.spinning_wheel_spinning.hint_tooltip"));
        }
    }
}
