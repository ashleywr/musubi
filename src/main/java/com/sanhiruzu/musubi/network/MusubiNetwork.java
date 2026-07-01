package com.sanhiruzu.musubi.network;

import com.sanhiruzu.musubi.MusubiClient;
import com.sanhiruzu.musubi.item.TamaItem;
import net.minecraft.client.player.LocalPlayer;
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
        registrar.playToClient(TamaBindVfxPayload.TYPE, TamaBindVfxPayload.STREAM_CODEC, MusubiNetwork::handleTamaBindVfx);
        registrar.playToClient(com.sanhiruzu.musubi.network.ItomakiUnspoolVfxPayload.TYPE,
            com.sanhiruzu.musubi.network.ItomakiUnspoolVfxPayload.STREAM_CODEC, MusubiNetwork::handleItomakiUnspool);
        registrar.playToClient(MoonRiteVfxPayload.TYPE, MoonRiteVfxPayload.STREAM_CODEC, MusubiNetwork::handleMoonRiteVfx);
    }

    private static void handleAttune(AttuneKeyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                TamaItem.attuneFromKey(player);
            }
        });
    }

    private static void handleTamaBindVfx(TamaBindVfxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof LocalPlayer player) {
                MusubiClient.playTamaBindVfx(player.level(), payload);
            }
        });
    }

    private static void handleItomakiUnspool(com.sanhiruzu.musubi.network.ItomakiUnspoolVfxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof LocalPlayer player) {
                MusubiClient.playItomakiUnspool(player.level(), payload);
            }
        });
    }

    private static void handleMoonRiteVfx(MoonRiteVfxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof LocalPlayer player) {
                MusubiClient.playMoonRiteVfx(player.level(), payload);
            }
        });
    }
}
