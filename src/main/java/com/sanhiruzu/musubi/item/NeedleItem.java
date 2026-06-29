package com.sanhiruzu.musubi.item;

import net.minecraft.world.item.Item;

public class NeedleItem extends Item {

    private final int maxCharges;

    public NeedleItem(int maxCharges, Properties properties) {
        super(properties);
        this.maxCharges = maxCharges;
    }

    public int getMaxCharges() {
        return maxCharges;
    }

    // TODO: sewing interaction (startup + drain phases), charge tracking via DataComponent,
    //       Curios slot registration, recharge via FiberBundleItem use-on
}
