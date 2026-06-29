package com.sanhiruzu.musubi.loot;

import com.mojang.serialization.MapCodec;
import com.sanhiruzu.musubi.Musubi;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class MusubiLootModifiers {

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Musubi.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<AddSilkwormModifier>> ADD_SILKWORM =
        LOOT_MODIFIERS.register("add_silkworm", AddSilkwormModifier.CODEC);
}
