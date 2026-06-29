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
    // Tama (玉) — weighted kumihimo bobbins; the tool that winds items into silk strands.

    public static final DeferredHolder<Item, TamaItem> BONE_TAMA =
        ITEMS.register("bone_tama", () -> new TamaItem(0, new Item.Properties().durability(64)));

    public static final DeferredHolder<Item, TamaItem> IRON_TAMA =
        ITEMS.register("iron_tama", () -> new TamaItem(1, new Item.Properties().durability(250)));

    public static final DeferredHolder<Item, TamaItem> SILVER_TAMA =
        ITEMS.register("silver_tama", () -> new TamaItem(2, new Item.Properties().durability(500)));

    public static final DeferredHolder<Item, TamaItem> ORIHIME_TAMA =
        ITEMS.register("orihime_tama", () -> new TamaItem(3, new Item.Properties().durability(1000)));

    public static final DeferredHolder<Item, SkeinItem> SKEIN =
        ITEMS.register("skein", () -> new SkeinItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, FiberBundleItem> FIBER_BUNDLE =
        ITEMS.register("fiber_bundle", () -> new FiberBundleItem(new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Item, Item> SILK_THREAD =
        ITEMS.register("silk_thread", () -> new Item(new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Item, MagicalSilkThreadItem> MAGICAL_SILK_THREAD =
        ITEMS.register("magical_silk_thread", () -> new MagicalSilkThreadItem(new Item.Properties().stacksTo(64)));

    public static final DeferredHolder<Item, SilkwormItem> SILKWORM =
        ITEMS.register("silkworm", () -> new SilkwormItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<Item, Item> SILKWORM_COCOON =
        ITEMS.register("silkworm_cocoon", () -> new Item(new Item.Properties().stacksTo(64)));
}
