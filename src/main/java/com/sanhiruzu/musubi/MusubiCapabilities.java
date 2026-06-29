package com.sanhiruzu.musubi;

import com.sanhiruzu.musubi.blockentity.MusubiBlockEntities;
import com.sanhiruzu.musubi.blockentity.MarudaiBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class MusubiCapabilities {

    /**
     * Registers NeoForge capabilities for all Musubi block entities.
     * Called from the mod event bus in Musubi.java.
     *
     * MarudaiBlockEntity exposes IItemHandler so that vanilla hoppers (patched by
     * NeoForge) and hopper-like mod blocks (Create, Supplementaries, etc.) can push
     * items into the marudai for automatic routing to nearby spool racks.
     */
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            MusubiBlockEntities.MARUDAI.get(),
            (be, side) -> be.getItemHandler()
        );
    }
}
