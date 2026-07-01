package com.sanhiruzu.musubi.component;

import com.sanhiruzu.musubi.Musubi;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MusubiDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Musubi.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SkeinContents>> SKEIN_CONTENTS =
        COMPONENTS.register("skein_contents", () -> DataComponentType.<SkeinContents>builder()
            .persistent(SkeinContents.CODEC)
            .networkSynchronized(SkeinContents.STREAM_CODEC)
            .build());

    /** The Tama's learned filter — item types it binds on pickup. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TamaFilter>> TAMA_FILTER =
        COMPONENTS.register("tama_filter", () -> DataComponentType.<TamaFilter>builder()
            .persistent(TamaFilter.CODEC)
            .networkSynchronized(TamaFilter.STREAM_CODEC)
            .build());

    /** The bound essence carried inside a single Tama. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TamaStorage>> TAMA_STORAGE =
        COMPONENTS.register("tama_storage", () -> DataComponentType.<TamaStorage>builder()
            .persistent(TamaStorage.CODEC)
            .networkSynchronized(TamaStorage.STREAM_CODEC)
            .build());

    /** Marudai-woven augment threads installed into a Tama. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TamaAugments>> TAMA_AUGMENTS =
        COMPONENTS.register("tama_augments", () -> DataComponentType.<TamaAugments>builder()
            .persistent(TamaAugments.CODEC)
            .networkSynchronized(TamaAugments.STREAM_CODEC)
            .build());

    /** The item a Weaving Pattern is encoded to produce in the loom (count always 1). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemStack>> PATTERN_TARGET =
        COMPONENTS.register("pattern_target", () -> DataComponentType.<ItemStack>builder()
            .persistent(ItemStack.CODEC)
            .networkSynchronized(ItemStack.STREAM_CODEC)
            .build());
}
