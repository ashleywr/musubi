package com.sanhiruzu.musubi.compat.jei;

import com.sanhiruzu.musubi.item.MusubiItems;
import net.minecraft.world.item.ItemStack;

/**
 * Represents the silk thread → magical silk thread spinning recipe shown in JEI.
 * There is exactly one instance: 1 silk thread → 1 magical silk thread.
 */
public final class SpinningWheelSpinningRecipe {

    public static final SpinningWheelSpinningRecipe INSTANCE = new SpinningWheelSpinningRecipe();

    public ItemStack input()  { return new ItemStack(MusubiItems.SILK_THREAD.get()); }
    public ItemStack output() { return new ItemStack(MusubiItems.MAGICAL_SILK_THREAD.get()); }
}
