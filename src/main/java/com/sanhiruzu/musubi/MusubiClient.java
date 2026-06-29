package com.sanhiruzu.musubi;

import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.item.MusubiItems;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

public class MusubiClient {

    public static void init(FMLClientSetupEvent event) {
        // Client-side registration (key bindings, renderers, etc.)
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) return -1;
            SkeinContents contents = stack.get(MusubiDataComponents.SKEIN_CONTENTS);
            return contents != null ? (contents.displayColor() | 0xFF000000) : -1;
        }, MusubiItems.SKEIN.get());
    }
}
