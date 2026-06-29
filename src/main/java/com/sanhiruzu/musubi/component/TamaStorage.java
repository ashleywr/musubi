package com.sanhiruzu.musubi.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * The bound essence carried inside a single Tama — a flat (itemId, count) ledger.
 *
 * Everything the Tama binds on pickup accumulates here, so a whole gathering haul
 * lives in one inventory slot. Total stored is capped per tier
 * ({@code TamaItem.storageCapacity}); entries keep first-seen order for stable display.
 */
public record TamaStorage(List<SkeinContents.VariantEntry> entries) {

    public static final TamaStorage EMPTY = new TamaStorage(List.of());

    public static final Codec<TamaStorage> CODEC =
        SkeinContents.VariantEntry.CODEC.listOf()
            .xmap(l -> new TamaStorage(List.copyOf(l)), TamaStorage::entries);

    public static final StreamCodec<RegistryFriendlyByteBuf, TamaStorage> STREAM_CODEC =
        StreamCodec.of(
            (buf, s) -> SkeinContents.VariantEntry.STREAM_CODEC.apply(ByteBufCodecs.list())
                .encode(buf, s.entries),
            buf -> new TamaStorage(List.copyOf(
                SkeinContents.VariantEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)))
        );

    public int total() {
        int t = 0;
        for (SkeinContents.VariantEntry e : entries) t += e.count();
        return t;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int distinctTypes() {
        return entries.size();
    }

    /** Returns a copy with {@code amount} of {@code id} added, merging into an existing entry. */
    public TamaStorage withAdded(ResourceLocation id, int amount) {
        LinkedHashMap<ResourceLocation, Integer> merged = new LinkedHashMap<>();
        for (SkeinContents.VariantEntry e : entries) merged.merge(e.itemId(), e.count(), Integer::sum);
        merged.merge(id, amount, Integer::sum);
        List<SkeinContents.VariantEntry> next = new ArrayList<>(merged.size());
        merged.forEach((k, v) -> next.add(new SkeinContents.VariantEntry(k, v)));
        return new TamaStorage(List.copyOf(next));
    }
}
