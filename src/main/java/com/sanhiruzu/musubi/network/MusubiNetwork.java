package com.sanhiruzu.musubi.network;

import com.sanhiruzu.musubi.item.TamaItem;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers Musubi's network payloads and their server-side handlers. */
public final class MusubiNetwork {

    private MusubiNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(AttuneKeyPayload.TYPE, AttuneKeyPayload.STREAM_CODEC, MusubiNetwork::handleAttune);
    }

    private static void handleAttune(AttuneKeyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                TamaItem.attuneFromKey(player);
            }
        });
    }
}
