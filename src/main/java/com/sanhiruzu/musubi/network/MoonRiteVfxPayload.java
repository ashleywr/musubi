package com.sanhiruzu.musubi.network;

import com.sanhiruzu.musubi.Musubi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.atomic.AtomicLong;

/** Clientbound payload that asks clients to render the moon rite response at a water source. */
public record MoonRiteVfxPayload(
    long eventId,
    long startTick,
    BlockPos pos,
    int durationTicks
) implements CustomPacketPayload {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(1L);

    public static final Type<MoonRiteVfxPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, "moon_rite_vfx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoonRiteVfxPayload> STREAM_CODEC =
        StreamCodec.of(MoonRiteVfxPayload::encode, MoonRiteVfxPayload::decode);

    public static MoonRiteVfxPayload create(BlockPos pos, long startTick, int durationTicks) {
        return new MoonRiteVfxPayload(NEXT_EVENT_ID.getAndIncrement(), startTick, pos, durationTicks);
    }

    private static void encode(RegistryFriendlyByteBuf buf, MoonRiteVfxPayload payload) {
        ByteBufCodecs.VAR_LONG.encode(buf, payload.eventId);
        ByteBufCodecs.VAR_LONG.encode(buf, payload.startTick);
        BlockPos.STREAM_CODEC.encode(buf, payload.pos);
        ByteBufCodecs.VAR_INT.encode(buf, payload.durationTicks);
    }

    private static MoonRiteVfxPayload decode(RegistryFriendlyByteBuf buf) {
        return new MoonRiteVfxPayload(
            ByteBufCodecs.VAR_LONG.decode(buf),
            ByteBufCodecs.VAR_LONG.decode(buf),
            BlockPos.STREAM_CODEC.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
