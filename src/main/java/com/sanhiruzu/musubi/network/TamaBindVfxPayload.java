package com.sanhiruzu.musubi.network;

import com.sanhiruzu.musubi.Musubi;
import com.sanhiruzu.musubi.curio.MusubiCurios;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicLong;

/** Clientbound payload that asks clients to render the tama bind/wrap VFX. */
public record TamaBindVfxPayload(
    long eventId,
    long startTick,
    int sourceEntityId,
    int targetEntityId,
    double sourceX,
    double sourceY,
    double sourceZ,
    double targetX,
    double targetY,
    double targetZ,
    ResourceLocation itemId,
    int stackCount,
    int durationTicks,
    boolean hideSourceEntity
) implements CustomPacketPayload {

    private static final AtomicLong NEXT_EVENT_ID = new AtomicLong(1L);

    public static final Type<TamaBindVfxPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, "tama_bind_vfx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TamaBindVfxPayload> STREAM_CODEC =
        StreamCodec.of(TamaBindVfxPayload::encode, TamaBindVfxPayload::decode);

    public static TamaBindVfxPayload wrap(Player player, ResourceLocation itemId, int durationTicks) {
        Vec3 source = player.position().add(0.0, 0.18, 0.0);
        Vec3 target = MusubiCurios.beltAnchor(player);
        long startTick = player.level().getGameTime();
        long eventId = NEXT_EVENT_ID.getAndIncrement();
        return new TamaBindVfxPayload(
            eventId, startTick, -1, player.getId(),
            source.x, source.y, source.z,
            target.x, target.y, target.z,
            itemId, 1, durationTicks, false
        );
    }

    public static TamaBindVfxPayload collection(ItemEntity source, Player target, Vec3 targetPos,
                                                 ResourceLocation itemId, int stackCount, int durationTicks,
                                                 long startTick) {
        Vec3 sourcePos = source.position().add(0.0, 0.08, 0.0);
        long eventId = NEXT_EVENT_ID.getAndIncrement();
        return new TamaBindVfxPayload(
            eventId, startTick, source.getId(), target.getId(),
            sourcePos.x, sourcePos.y, sourcePos.z,
            targetPos.x, targetPos.y, targetPos.z,
            itemId, Math.max(1, stackCount), durationTicks, true
        );
    }

    private static void encode(RegistryFriendlyByteBuf buf, TamaBindVfxPayload payload) {
        ByteBufCodecs.VAR_LONG.encode(buf, payload.eventId);
        ByteBufCodecs.VAR_LONG.encode(buf, payload.startTick);
        ByteBufCodecs.VAR_INT.encode(buf, payload.sourceEntityId);
        ByteBufCodecs.VAR_INT.encode(buf, payload.targetEntityId);
        ByteBufCodecs.DOUBLE.encode(buf, payload.sourceX);
        ByteBufCodecs.DOUBLE.encode(buf, payload.sourceY);
        ByteBufCodecs.DOUBLE.encode(buf, payload.sourceZ);
        ByteBufCodecs.DOUBLE.encode(buf, payload.targetX);
        ByteBufCodecs.DOUBLE.encode(buf, payload.targetY);
        ByteBufCodecs.DOUBLE.encode(buf, payload.targetZ);
        ResourceLocation.STREAM_CODEC.encode(buf, payload.itemId);
        ByteBufCodecs.VAR_INT.encode(buf, payload.stackCount);
        ByteBufCodecs.VAR_INT.encode(buf, payload.durationTicks);
        ByteBufCodecs.BOOL.encode(buf, payload.hideSourceEntity);
    }

    private static TamaBindVfxPayload decode(RegistryFriendlyByteBuf buf) {
        return new TamaBindVfxPayload(
            ByteBufCodecs.VAR_LONG.decode(buf),
            ByteBufCodecs.VAR_LONG.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.DOUBLE.decode(buf),
            ByteBufCodecs.DOUBLE.decode(buf),
            ByteBufCodecs.DOUBLE.decode(buf),
            ByteBufCodecs.DOUBLE.decode(buf),
            ByteBufCodecs.DOUBLE.decode(buf),
            ByteBufCodecs.DOUBLE.decode(buf),
            ResourceLocation.STREAM_CODEC.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.BOOL.decode(buf)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
