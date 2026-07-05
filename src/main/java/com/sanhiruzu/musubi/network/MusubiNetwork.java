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
        registrar.playToServer(HikidashiExtractPayload.TYPE, HikidashiExtractPayload.STREAM_CODEC, MusubiNetwork::handleHikidashiExtract);
        registrar.playToClient(TamaBindVfxPayload.TYPE, TamaBindVfxPayload.STREAM_CODEC, MusubiNetwork::handleTamaBindVfx);
        registrar.playToClient(com.sanhiruzu.musubi.network.ItomakiUnspoolVfxPayload.TYPE,
            com.sanhiruzu.musubi.network.ItomakiUnspoolVfxPayload.STREAM_CODEC, MusubiNetwork::handleItomakiUnspool);
        registrar.playToClient(MoonRiteVfxPayload.TYPE, MoonRiteVfxPayload.STREAM_CODEC, MusubiNetwork::handleMoonRiteVfx);
        registrar.playToClient(CometVfxPayload.TYPE, CometVfxPayload.STREAM_CODEC, MusubiNetwork::handleCometVfx);
        registrar.playToClient(OpenHikidashiScreenPayload.TYPE, OpenHikidashiScreenPayload.STREAM_CODEC, MusubiNetwork::handleOpenHikidashiScreen);
    }

    private static void handleAttune(AttuneKeyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                TamaItem.attuneFromKey(player);
            }
        });
    }

    private static void handleHikidashiExtract(HikidashiExtractPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
                if (level.getBlockEntity(payload.blockPos()) instanceof com.sanhiruzu.musubi.blockentity.HikidashiBlockEntity be) {
                    int extracted = be.extractItem(payload.itemId(), payload.amount());
                    if (extracted > 0) {
                        var skeinItem = com.sanhiruzu.musubi.item.MusubiItems.SKEIN.get();
                        var stack = new net.minecraft.world.item.ItemStack(skeinItem);
                        var variant = new com.sanhiruzu.musubi.component.SkeinContents.VariantEntry(payload.itemId(), extracted);
                        var skein = new com.sanhiruzu.musubi.component.SkeinContents(
                            0, // tier 0 (default)
                            payload.itemId().toString(),
                            extracted,
                            java.util.List.of(variant),
                            0xC8A864, // golden fallback color
                            0, // complexity score
                            java.util.Optional.empty()
                        );
                        stack.set(com.sanhiruzu.musubi.component.MusubiDataComponents.SKEIN_CONTENTS, skein);
                        com.sanhiruzu.musubi.util.PlayerInventoryUtil.giveDirect(player, stack);
                    }
                }
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

    private static void handleCometVfx(CometVfxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof LocalPlayer player) {
                MusubiClient.playCometVfx(player.level(), payload);
            }
        });
    }

    private static void handleOpenHikidashiScreen(OpenHikidashiScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof LocalPlayer) {
                MusubiClient.openHikidashiScreen(payload.blockPos(), payload.storage());
            }
        });
    }
}
