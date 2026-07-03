package com.sanhiruzu.musubi.network;

import com.sanhiruzu.musubi.Musubi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Clientbound payload announcing a comet crossing the sky.
 *
 * <p>The comet is a celestial object: every player sees it in the same sky
 * direction, and its position is a pure function of game time (which is already
 * synchronized), so this one packet at the start of the crossing is all the
 * network traffic the whole event needs. The trajectory is a great-circle arc
 * from the start direction to the end direction, in degrees of azimuth
 * (Minecraft yaw convention: 0 = south/+Z, 90 = west/-X) and elevation above
 * the horizon.</p>
 */
public record CometVfxPayload(
    long eventId,
    long startTick,
    int durationTicks,
    float startAzimuthDeg,
    float startElevationDeg,
    float endAzimuthDeg,
    float endElevationDeg
) implements CustomPacketPayload {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(1L);

    public static final Type<CometVfxPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, "comet_vfx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CometVfxPayload> STREAM_CODEC =
        StreamCodec.of(CometVfxPayload::encode, CometVfxPayload::decode);

    public static CometVfxPayload create(long startTick, int durationTicks,
                                         float startAzimuthDeg, float startElevationDeg,
                                         float endAzimuthDeg, float endElevationDeg) {
        return new CometVfxPayload(NEXT_EVENT_ID.getAndIncrement(), startTick, durationTicks,
            startAzimuthDeg, startElevationDeg, endAzimuthDeg, endElevationDeg);
    }

    private static void encode(RegistryFriendlyByteBuf buf, CometVfxPayload payload) {
        ByteBufCodecs.VAR_LONG.encode(buf, payload.eventId);
        ByteBufCodecs.VAR_LONG.encode(buf, payload.startTick);
        ByteBufCodecs.VAR_INT.encode(buf, payload.durationTicks);
        ByteBufCodecs.FLOAT.encode(buf, payload.startAzimuthDeg);
        ByteBufCodecs.FLOAT.encode(buf, payload.startElevationDeg);
        ByteBufCodecs.FLOAT.encode(buf, payload.endAzimuthDeg);
        ByteBufCodecs.FLOAT.encode(buf, payload.endElevationDeg);
    }

    private static CometVfxPayload decode(RegistryFriendlyByteBuf buf) {
        return new CometVfxPayload(
            ByteBufCodecs.VAR_LONG.decode(buf),
            ByteBufCodecs.VAR_LONG.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.FLOAT.decode(buf),
            ByteBufCodecs.FLOAT.decode(buf),
            ByteBufCodecs.FLOAT.decode(buf),
            ByteBufCodecs.FLOAT.decode(buf)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
