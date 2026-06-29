package com.sanhiruzu.musubi.component;

import com.sanhiruzu.musubi.Musubi;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
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
}
