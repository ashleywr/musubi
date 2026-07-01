package com.sanhiruzu.musubi;

import com.sanhiruzu.musubi.blockentity.MusubiBlockEntities;
import com.sanhiruzu.musubi.rei.ReiCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class MusubiCapabilities {

    /**
     * Registers NeoForge capabilities for all Musubi block entities.
     * Called from the mod event bus in Musubi.java.
     */
    public static void register(RegisterCapabilitiesEvent event) {
        // Hopper/item-handler support for Marudai.
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            MusubiBlockEntities.MARUDAI.get(),
            (be, side) -> be.getItemHandler()
        );

        // REI storage for the Shrine Stone.
        event.registerBlockEntity(
            ReiCapability.BLOCK,
            MusubiBlockEntities.SHRINE_STONE.get(),
            (be, ctx) -> be.getReiStorage()
        );

        // REI storage for the Donation Box.
        event.registerBlockEntity(
            ReiCapability.BLOCK,
            MusubiBlockEntities.DONATION_BOX.get(),
            (be, ctx) -> be.getReiStorage()
        );

        // REI storage for the Shinboku.
        event.registerBlockEntity(
            ReiCapability.BLOCK,
            MusubiBlockEntities.SHINBOKU.get(),
            (be, ctx) -> be.getReiStorage()
        );

        // REI storage for the Spinning Wheel (generated when spinning magical silk thread).
        event.registerBlockEntity(
            ReiCapability.BLOCK,
            MusubiBlockEntities.SPINNING_WHEEL.get(),
            (be, ctx) -> be.getReiStorage()
        );

        // REI storage for the Loom (consumes REI while weaving).
        event.registerBlockEntity(
            ReiCapability.BLOCK,
            MusubiBlockEntities.LOOM.get(),
            (be, ctx) -> be.getReiStorage()
        );

        // Item handler for the Loom (hopper input/output support).
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            MusubiBlockEntities.LOOM.get(),
            (be, side) -> be.getItemHandler()
        );
    }
}
