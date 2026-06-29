package com.sanhiruzu.musubi.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DataComponent stored on every skein item.
 *
 * groupKey is a tag ID (e.g. "minecraft:logs") for bone/silver needles, or an item ID
 * (e.g. "minecraft:oak_log") for iron/orihime needles. Two skeins merge only when both
 * needleTier and groupKey match.
 *
 * variants tracks per-item counts in FIFO order for accurate retrieval. count is the
 * sum of all variant counts, cached here to avoid summing on every tooltip render.
 *
 * displayColor is RGB packed as 0xRRGGBB, sampled from the first item sewn and stored
 * at sewing time — no runtime texture sampling ever occurs.
 *
 * dataRef is non-empty only for complex items (complexityScore >= 3) whose full
 * DataComponent payload is stored in MusubiSavedData.
 */
public record SkeinContents(
    int needleTier,
    String groupKey,
    int count,
    List<VariantEntry> variants,
    int displayColor,
    int complexityScore,
    Optional<UUID> dataRef
) {

    public record VariantEntry(ResourceLocation itemId, int count) {

        public static final Codec<VariantEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(VariantEntry::itemId),
            Codec.INT.fieldOf("count").forGetter(VariantEntry::count)
        ).apply(inst, VariantEntry::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, VariantEntry> STREAM_CODEC =
            StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, VariantEntry::itemId,
                ByteBufCodecs.VAR_INT,          VariantEntry::count,
                VariantEntry::new
            );
    }

    public static final Codec<SkeinContents> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.intRange(0, 3).fieldOf("needle_tier").forGetter(SkeinContents::needleTier),
        Codec.STRING.fieldOf("group_key").forGetter(SkeinContents::groupKey),
        Codec.INT.fieldOf("count").forGetter(SkeinContents::count),
        VariantEntry.CODEC.listOf().fieldOf("variants").forGetter(SkeinContents::variants),
        Codec.INT.fieldOf("display_color").forGetter(SkeinContents::displayColor),
        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("complexity_score").forGetter(SkeinContents::complexityScore),
        Codec.STRING.<UUID>xmap(UUID::fromString, UUID::toString).optionalFieldOf("data_ref").forGetter(SkeinContents::dataRef)
    ).apply(inst, SkeinContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkeinContents> STREAM_CODEC =
        StreamCodec.of(
            (buf, sc) -> {
                ByteBufCodecs.VAR_INT.encode(buf, sc.needleTier());
                ByteBufCodecs.STRING_UTF8.encode(buf, sc.groupKey());
                ByteBufCodecs.VAR_INT.encode(buf, sc.count());
                VariantEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, sc.variants());
                ByteBufCodecs.INT.encode(buf, sc.displayColor());
                ByteBufCodecs.VAR_INT.encode(buf, sc.complexityScore());
                ByteBufCodecs.optional(uuidStreamCodec()).encode(buf, sc.dataRef());
            },
            buf -> new SkeinContents(
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                VariantEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                ByteBufCodecs.INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.optional(uuidStreamCodec()).decode(buf)
            )
        );

    /** Whether this skein can merge counts with another in the same inventory slot. */
    public boolean mergesWith(SkeinContents other) {
        return this.needleTier == other.needleTier && this.groupKey.equals(other.groupKey);
    }

    /** True for bone and silver needles (tag-grouped); false for iron and orihime (item-exact). */
    public boolean isTagGrouped() {
        return needleTier == 0 || needleTier == 2;
    }

    private static StreamCodec<RegistryFriendlyByteBuf, UUID> uuidStreamCodec() {
        return StreamCodec.of(
            (buf, uuid) -> {
                buf.writeLong(uuid.getMostSignificantBits());
                buf.writeLong(uuid.getLeastSignificantBits());
            },
            buf -> new UUID(buf.readLong(), buf.readLong())
        );
    }
}
