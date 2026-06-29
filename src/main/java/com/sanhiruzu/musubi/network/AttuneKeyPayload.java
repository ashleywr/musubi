package com.sanhiruzu.musubi.network;

import com.sanhiruzu.musubi.Musubi;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Empty C2S trigger sent when the player presses the "attune Tama" keybind. */
public record AttuneKeyPayload() implements CustomPacketPayload {

    public static final Type<AttuneKeyPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, "attune_key"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AttuneKeyPayload> STREAM_CODEC =
        StreamCodec.unit(new AttuneKeyPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
