package com.sanhiruzu.musubi.client;

import com.sanhiruzu.musubi.network.AttuneKeyPayload;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client keybind for attuning the active Tama to the held/off-hand sample. */
public final class MusubiKeybinds {

    private MusubiKeybinds() {}

    public static final KeyMapping ATTUNE = new KeyMapping(
        "key.musubi.attune", GLFW.GLFW_KEY_V, "key.categories.musubi");

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(ATTUNE);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        while (ATTUNE.consumeClick()) {
            PacketDistributor.sendToServer(new AttuneKeyPayload());
        }
    }
}
