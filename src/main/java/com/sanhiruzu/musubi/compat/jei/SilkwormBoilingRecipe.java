package com.sanhiruzu.musubi.compat.jei;

import com.sanhiruzu.musubi.item.MusubiItems;
import net.minecraft.world.item.ItemStack;

/**
 * Represents the single silkworm cocoon → silk thread boiling recipe shown in JEI.
 * There is exactly one instance: 1 cocoon → 1 silk thread, 1:1.
 */
public final class SilkwormBoilingRecipe {

    public static final SilkwormBoilingRecipe INSTANCE = new SilkwormBoilingRecipe();

    public ItemStack input()  { return new ItemStack(MusubiItems.SILKWORM_COCOON.get()); }
    public ItemStack output() { return new ItemStack(MusubiItems.SILK_THREAD.get()); }
    public ItemStack fire()   { return new ItemStack(net.minecraft.world.item.Items.CAMPFIRE); }
}
