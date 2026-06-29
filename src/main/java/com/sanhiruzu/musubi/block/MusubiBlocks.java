package com.sanhiruzu.musubi.block;

import com.sanhiruzu.musubi.Musubi;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class MusubiBlocks {

    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, Musubi.MOD_ID);

    public static final DeferredRegister<Item> BLOCK_ITEMS =
        DeferredRegister.create(Registries.ITEM, Musubi.MOD_ID);

    public static final DeferredHolder<Block, SpoolRackBlock> SPOOL_RACK =
        register("spool_rack", () -> new SpoolRackBlock(
            BlockBehaviour.Properties.of()
                .strength(1.5f, 6.0f)
                .sound(SoundType.WOOD)
                .noOcclusion()
        ));

    public static final DeferredHolder<Block, MarudaiBlock> MARUDAI =
        register("marudai", () -> new MarudaiBlock(
            BlockBehaviour.Properties.of()
                .strength(2.0f, 6.0f)
                .sound(SoundType.WOOD)
                .noOcclusion()
        ));

    // Pending:
    //
    // public static final DeferredHolder<Block, SpinningWheelBlock> SPINNING_WHEEL =
    //     register("spinning_wheel", () -> new SpinningWheelBlock(...));
    //     Role: raw fiber → fiber bundles (needle recharge). Entry point to the textile chain.

    private static <T extends Block> DeferredHolder<Block, T> register(String name, Supplier<T> block) {
        DeferredHolder<Block, T> holder = BLOCKS.register(name, block);
        BLOCK_ITEMS.register(name, () -> new BlockItem(holder.get(), new Item.Properties()));
        return holder;
    }
}
