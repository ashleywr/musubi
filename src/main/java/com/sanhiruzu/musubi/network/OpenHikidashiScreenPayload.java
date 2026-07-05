package com.sanhiruzu.musubi.network;

import com.sanhiruzu.musubi.Musubi;
import com.sanhiruzu.musubi.component.SkeinContents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent from server to client when opening a Hikidashi storage GUI.
 * Contains the block position and a list of all accessible SkeinContents from nearby racks.
 */
public record OpenHikidashiScreenPayload(
    BlockPos blockPos,
    List<SkeinContents> storage
) implements CustomPacketPayload {

    public static final Type<OpenHikidashiScreenPayload> TYPE =
        new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, "open_hikidashi_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenHikidashiScreenPayload> STREAM_CODEC =
        StreamCodec.of(OpenHikidashiScreenPayload::encode, OpenHikidashiScreenPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, OpenHikidashiScreenPayload payload) {
        buf.writeLong(payload.blockPos.asLong());
        ByteBufCodecs.VAR_INT.encode(buf, payload.storage.size());
        for (SkeinContents skein : payload.storage) {
            SkeinContents.STREAM_CODEC.encode(buf, skein);
        }
    }

    private static OpenHikidashiScreenPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = BlockPos.of(buf.readLong());
        int size = ByteBufCodecs.VAR_INT.decode(buf);
        List<SkeinContents> storage = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            storage.add(SkeinContents.STREAM_CODEC.decode(buf));
        }
        return new OpenHikidashiScreenPayload(pos, storage);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
