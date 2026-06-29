package com.sanhiruzu.musubi.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tama's learned filter — the set of item types it binds on pickup.
 *
 * Built by example: the player sneak-designates an item to toggle its type here.
 * Only item ids present in this list are bound into the Tama; everything else
 * (tools, food, untaught drops) is left alone in the normal inventory.
 *
 * The number of types a Tama may hold is capped per tier ({@code TamaItem.filterCapacity}).
 */
public record TamaFilter(List<ResourceLocation> items) {

    public static final TamaFilter EMPTY = new TamaFilter(List.of());

    public static final Codec<TamaFilter> CODEC =
        ResourceLocation.CODEC.listOf().xmap(l -> new TamaFilter(List.copyOf(l)), TamaFilter::items);

    public static final StreamCodec<RegistryFriendlyByteBuf, TamaFilter> STREAM_CODEC =
        StreamCodec.of(
            (buf, f) -> {
                buf.writeVarInt(f.items.size());
                for (ResourceLocation id : f.items) ResourceLocation.STREAM_CODEC.encode(buf, id);
            },
            buf -> {
                int n = buf.readVarInt();
                List<ResourceLocation> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(ResourceLocation.STREAM_CODEC.decode(buf));
                return new TamaFilter(List.copyOf(list));
            }
        );

    public boolean contains(ResourceLocation id) {
        return items.contains(id);
    }

    public int size() {
        return items.size();
    }

    /** Returns a copy with {@code id} added (no-op if already present). */
    public TamaFilter with(ResourceLocation id) {
        if (items.contains(id)) return this;
        List<ResourceLocation> next = new ArrayList<>(items);
        next.add(id);
        return new TamaFilter(List.copyOf(next));
    }

    /** Returns a copy with {@code id} removed (no-op if absent). */
    public TamaFilter without(ResourceLocation id) {
        if (!items.contains(id)) return this;
        List<ResourceLocation> next = new ArrayList<>(items);
        next.remove(id);
        return new TamaFilter(List.copyOf(next));
    }
}
