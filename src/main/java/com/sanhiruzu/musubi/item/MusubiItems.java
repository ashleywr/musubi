package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.Musubi;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MusubiItems {

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, Musubi.MOD_ID);

    // Durability chosen to feel like tool tiers: bone~wood, iron~iron, silver~diamond, orihime~netherite.
    // Each session costs 1 durability (at Phase 2 entry).

    public static final DeferredHolder<Item, NeedleItem> BONE_NEEDLE =
        ITEMS.register("bone_needle", () -> new NeedleItem(0, new Item.Properties().durability(64)));

    public static final DeferredHolder<Item, NeedleItem> IRON_NEEDLE =
        ITEMS.register("iron_needle", () -> new NeedleItem(1, new Item.Properties().durability(250)));

    public static final DeferredHolder<Item, NeedleItem> SILVER_NEEDLE =
        ITEMS.register("silver_needle", () -> new NeedleItem(2, new Item.Properties().durability(500)));

    public static final DeferredHolder<Item, NeedleItem> ORIHIME_NEEDLE =
        ITEMS.register("orihime_needle", () -> new NeedleItem(3, new Item.Properties().durability(1000)));

    public static final DeferredHolder<Item, SkeinItem> SKEIN =
        ITEMS.register("skein", () -> new SkeinItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, FiberBundleItem> FIBER_BUNDLE =
        ITEMS.register("fiber_bundle", () -> new FiberBundleItem(new Item.Properties().stacksTo(64)));
}
