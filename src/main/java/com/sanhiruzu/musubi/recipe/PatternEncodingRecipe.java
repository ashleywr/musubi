package com.sanhiruzu.musubi.recipe;

import com.sanhiruzu.musubi.item.WeavingPatternItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Special crafting recipe: one Weaving Pattern + exactly one other item →
 * a Weaving Pattern encoded to produce that item.
 *
 * Re-encoding is allowed: feeding an already-encoded pattern overwrites its target.
 */
public class PatternEncodingRecipe extends CustomRecipe {

    public PatternEncodingRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean foundPattern = false;
        int samples = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof WeavingPatternItem) {
                if (foundPattern) return false; // only one pattern allowed
                foundPattern = true;
            } else {
                samples++;
            }
        }
        return foundPattern && samples == 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack sample = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty() || stack.getItem() instanceof WeavingPatternItem) continue;
            sample = stack;
        }
        if (sample.isEmpty()) return ItemStack.EMPTY;
        return WeavingPatternItem.encode(sample);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MusubiRecipes.PATTERN_ENCODING.get();
    }
}
