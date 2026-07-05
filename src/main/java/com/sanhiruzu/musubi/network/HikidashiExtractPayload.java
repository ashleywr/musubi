package com.sanhiruzu.musubi.network;

import com.sanhiruzu.musubi.Musubi;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent from client to server when extracting items from Hikidashi storage.
 * Contains the block position, item ID, and amount to extract.
 */
public record HikidashiExtractPayload(
    BlockPos blockPos,
    ResourceLocation itemId,
    int amount
) implements CustomPacketPayload {

    public static final Type<HikidashiExtractPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, "hikidashi_extract"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HikidashiExtractPayload> STREAM_CODEC =
        StreamCodec.of(HikidashiExtractPayload::encode, HikidashiExtractPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, HikidashiExtractPayload payload) {
        buf.writeLong(payload.blockPos.asLong());
        ResourceLocation.STREAM_CODEC.encode(buf, payload.itemId);
        ByteBufCodecs.VAR_INT.encode(buf, payload.amount);
    }

    private static HikidashiExtractPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = BlockPos.of(buf.readLong());
        ResourceLocation itemId = ResourceLocation.STREAM_CODEC.decode(buf);
        int amount = ByteBufCodecs.VAR_INT.decode(buf);
        return new HikidashiExtractPayload(pos, itemId, amount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
