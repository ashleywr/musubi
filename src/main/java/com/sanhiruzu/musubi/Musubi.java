package com.sanhiruzu.musubi;

import com.mojang.logging.LogUtils;
import com.sanhiruzu.musubi.block.MusubiBlocks;
import com.sanhiruzu.musubi.blockentity.MusubiBlockEntities;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.item.MusubiItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(Musubi.MOD_ID)
public class Musubi {

    public static final String MOD_ID = "musubi";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Musubi(IEventBus modEventBus, ModContainer modContainer) {
        MusubiDataComponents.COMPONENTS.register(modEventBus);
        MusubiItems.ITEMS.register(modEventBus);
        MusubiBlocks.BLOCKS.register(modEventBus);
        MusubiBlocks.BLOCK_ITEMS.register(modEventBus);
        MusubiBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        modEventBus.addListener(MusubiCapabilities::register);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(MusubiClient::init);
            modEventBus.addListener(MusubiClient::registerItemColors);
            modEventBus.addListener(MusubiClient::registerRenderers);
        }
    }
}
