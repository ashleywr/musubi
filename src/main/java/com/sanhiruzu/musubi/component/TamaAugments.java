package com.sanhiruzu.musubi.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Marudai-woven augment threads installed into a Tama.
 *
 * The component stores item ids so the same augment item can be checked, added, and removed
 * without inventing a second registry for augment metadata.
 */
public record TamaAugments(List<ResourceLocation> items) {

    public static final TamaAugments EMPTY = new TamaAugments(List.of());

    public static final Codec<TamaAugments> CODEC =
        ResourceLocation.CODEC.listOf().xmap(l -> new TamaAugments(List.copyOf(l)), TamaAugments::items);

    public static final StreamCodec<RegistryFriendlyByteBuf, TamaAugments> STREAM_CODEC =
        StreamCodec.of(
            (buf, augments) -> {
                buf.writeVarInt(augments.items.size());
                for (ResourceLocation id : augments.items) ResourceLocation.STREAM_CODEC.encode(buf, id);
            },
            buf -> {
                int n = buf.readVarInt();
                List<ResourceLocation> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(ResourceLocation.STREAM_CODEC.decode(buf));
                return new TamaAugments(List.copyOf(list));
            }
        );

    public boolean contains(ResourceLocation id) {
        return items.contains(id);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int size() {
        return items.size();
    }

    /** Returns a copy with {@code id} added; no-op if it is already present. */
    public TamaAugments with(ResourceLocation id) {
        if (items.contains(id)) return this;
        List<ResourceLocation> next = new ArrayList<>(items);
        next.add(id);
        return new TamaAugments(List.copyOf(next));
    }

    /** Returns a copy with {@code id} removed; no-op if it is absent. */
    public TamaAugments without(ResourceLocation id) {
        if (!items.contains(id)) return this;
        List<ResourceLocation> next = new ArrayList<>(items);
        next.remove(id);
        return new TamaAugments(List.copyOf(next));
    }
}
