package com.sanhiruzu.musubi.network;

import com.sanhiruzu.musubi.Musubi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side VFX for an Itomaki unspooling into nearby Spool Racks.
 * Threads flow from the Itomaki block toward the Rack peg positions in a reverse-collection look.
 *
 * Triggered from ItomakiBlockEntity.serverTick when items are deposited; the payload contains
 * enough context to replay the effect (source, target rack positions, and the item being moved).
 */
public record ItomakiUnspoolVfxPayload(
    double sourceX, double sourceY, double sourceZ,
    double[] targetX, double[] targetY, double[] targetZ,
    ResourceLocation itemId,
    long startTick
) implements CustomPacketPayload {

    public static final Type<ItomakiUnspoolVfxPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, "itomaki_unspool_vfx"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItomakiUnspoolVfxPayload> STREAM_CODEC =
        StreamCodec.of(ItomakiUnspoolVfxPayload::encode, ItomakiUnspoolVfxPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ItomakiUnspoolVfxPayload payload) {
        ByteBufCodecs.DOUBLE.encode(buf, payload.sourceX);
        ByteBufCodecs.DOUBLE.encode(buf, payload.sourceY);
        ByteBufCodecs.DOUBLE.encode(buf, payload.sourceZ);
        ByteBufCodecs.VAR_INT.encode(buf, payload.targetX.length);
        for (int i = 0; i < payload.targetX.length; i++) {
            ByteBufCodecs.DOUBLE.encode(buf, payload.targetX[i]);
            ByteBufCodecs.DOUBLE.encode(buf, payload.targetY[i]);
            ByteBufCodecs.DOUBLE.encode(buf, payload.targetZ[i]);
        }
        ResourceLocation.STREAM_CODEC.encode(buf, payload.itemId);
        ByteBufCodecs.VAR_LONG.encode(buf, payload.startTick);
    }

    private static ItomakiUnspoolVfxPayload decode(RegistryFriendlyByteBuf buf) {
        double sx = ByteBufCodecs.DOUBLE.decode(buf);
        double sy = ByteBufCodecs.DOUBLE.decode(buf);
        double sz = ByteBufCodecs.DOUBLE.decode(buf);
        int count = ByteBufCodecs.VAR_INT.decode(buf);
        double[] tx = new double[count], ty = new double[count], tz = new double[count];
        for (int i = 0; i < count; i++) {
            tx[i] = ByteBufCodecs.DOUBLE.decode(buf);
            ty[i] = ByteBufCodecs.DOUBLE.decode(buf);
            tz[i] = ByteBufCodecs.DOUBLE.decode(buf);
        }
        ResourceLocation itemId = ResourceLocation.STREAM_CODEC.decode(buf);
        long startTick = ByteBufCodecs.VAR_LONG.decode(buf);
        return new ItomakiUnspoolVfxPayload(sx, sy, sz, tx, ty, tz, itemId, startTick);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Creates a payload for an unspool effect.
     * {@code source} is the block center of the itomaki (BlockPos + 0.5).
     * {@code targets} are the peg positions on nearby spool racks where threads flow toward.
     * {@code itemId} is the item type being moved (used to tint the rope strands).
     * {@code startTick} is the game tick when the effect started (for replay/catchup).
     */
    public static ItomakiUnspoolVfxPayload create(Vec3 source, Vec3[] targets, ResourceLocation itemId, long startTick) {
        double[] tx = new double[targets.length];
        double[] ty = new double[targets.length];
        double[] tz = new double[targets.length];
        for (int i = 0; i < targets.length; i++) {
            tx[i] = targets[i].x;
            ty[i] = targets[i].y;
            tz[i] = targets[i].z;
        }
        return new ItomakiUnspoolVfxPayload(source.x, source.y, source.z, tx, ty, tz, itemId, startTick);
    }
}
