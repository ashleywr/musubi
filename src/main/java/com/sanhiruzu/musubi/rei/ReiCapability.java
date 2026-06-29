package com.sanhiruzu.musubi.rei;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;

public final class ReiCapability {

    /** The REI storage capability exposed by Musubi block entities. REI is non-directional. */
    public static final BlockCapability<IReiStorage, Void> BLOCK =
        BlockCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath("musubi", "rei_storage"),
            IReiStorage.class
        );

    private ReiCapability() {}
}
