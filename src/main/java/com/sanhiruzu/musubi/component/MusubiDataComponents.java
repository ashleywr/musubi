package com.sanhiruzu.musubi.component;

import com.sanhiruzu.musubi.Musubi;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MusubiDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Musubi.MOD_ID);

    // Registered once the storage design is implemented:
    //
    // public static final DeferredHolder<DataComponentType<?>, DataComponentType<SkeinContents>> SKEIN_CONTENTS =
    //     COMPONENTS.register("skein_contents", () -> DataComponentType.<SkeinContents>builder()
    //         .persistent(SkeinContents.CODEC)
    //         .networkSynchronized(SkeinContents.STREAM_CODEC)
    //         .build());
    //
    // public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> NEEDLE_CHARGES =
    //     COMPONENTS.register("needle_charges", () -> DataComponentType.<Integer>builder()
    //         .persistent(Codec.intRange(0, Integer.MAX_VALUE))
    //         .networkSynchronized(ByteBufCodecs.VAR_INT)
    //         .build());
}
