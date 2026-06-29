package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.Musubi;
import com.sanhiruzu.musubi.block.MusubiBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MusubiCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Musubi.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
        TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.musubi.main"))
            .icon(() -> new ItemStack(MusubiItems.SILK_THREAD.get()))
            .displayItems((params, output) -> {
                // Silkworm chain
                output.accept(MusubiItems.SILKWORM.get());
                output.accept(MusubiItems.SILKWORM_COCOON.get());
                output.accept(MusubiItems.SILK_THREAD.get());
                output.accept(MusubiItems.MAGICAL_SILK_THREAD.get());

                // Winding tools (tama)
                output.accept(MusubiItems.BONE_TAMA.get());
                output.accept(MusubiItems.IRON_TAMA.get());
                output.accept(MusubiItems.SILVER_TAMA.get());
                output.accept(MusubiItems.ORIHIME_TAMA.get());

                // Skeins and storage
                output.accept(MusubiItems.SKEIN.get());
                output.accept(MusubiItems.FIBER_BUNDLE.get());

                // Blocks
                output.accept(MusubiBlocks.SPOOL_RACK.get());
                output.accept(MusubiBlocks.MARUDAI.get());
                output.accept(MusubiBlocks.SPINNING_WHEEL.get());
            })
            .build());
}
