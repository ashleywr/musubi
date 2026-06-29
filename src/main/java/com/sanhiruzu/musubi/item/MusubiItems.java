package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.Musubi;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MusubiItems {

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, Musubi.MOD_ID);

    public static final DeferredHolder<Item, NeedleItem> BONE_NEEDLE =
        ITEMS.register("bone_needle", () -> new NeedleItem(12, new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, NeedleItem> IRON_NEEDLE =
        ITEMS.register("iron_needle", () -> new NeedleItem(28, new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<Item, SkeinItem> SKEIN =
        ITEMS.register("skein", () -> new SkeinItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, FiberBundleItem> FIBER_BUNDLE =
        ITEMS.register("fiber_bundle", () -> new FiberBundleItem(new Item.Properties().stacksTo(64)));
}
