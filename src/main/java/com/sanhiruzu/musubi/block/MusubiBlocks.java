package com.sanhiruzu.musubi.block;

import com.sanhiruzu.musubi.Musubi;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class MusubiBlocks {

    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, Musubi.MOD_ID);

    public static final DeferredRegister<Item> BLOCK_ITEMS =
        DeferredRegister.create(Registries.ITEM, Musubi.MOD_ID);

    // Blocks registered once designs are finalised:
    //
    // public static final DeferredHolder<Block, MarudaiBlock> MARUDAI =
    //     register("marudai", () -> new MarudaiBlock(...));
    //
    // public static final DeferredHolder<Block, SpoolRackBlock> SPOOL_RACK =
    //     register("spool_rack", () -> new SpoolRackBlock(...));

    private static <T extends Block> DeferredHolder<Block, T> register(String name, Supplier<T> block) {
        DeferredHolder<Block, T> holder = BLOCKS.register(name, block);
        BLOCK_ITEMS.register(name, () -> new BlockItem(holder.get(), new Item.Properties()));
        return holder;
    }
}
