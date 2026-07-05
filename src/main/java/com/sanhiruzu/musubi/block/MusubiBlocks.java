package com.sanhiruzu.musubi.block;

import com.sanhiruzu.musubi.Musubi;
import com.sanhiruzu.musubi.item.MusubiBlockItem;
import net.minecraft.core.registries.Registries;
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

    public static final DeferredHolder<Block, SpinningWheelBlock> SPINNING_WHEEL =
        register("spinning_wheel", () -> new SpinningWheelBlock(
            BlockBehaviour.Properties.of()
                .strength(2.0f, 6.0f)
                .sound(SoundType.WOOD)
                .noOcclusion()
        ));

    public static final DeferredHolder<Block, ShrineStoneBlock> SHRINE_STONE =
        register("shrine_stone", () -> new ShrineStoneBlock(
            BlockBehaviour.Properties.of()
                .strength(3.0f, 12.0f)
                .sound(SoundType.STONE)
                .lightLevel(state -> 4)
        ));

    public static final DeferredHolder<Block, ToriiBlock> TORII =
        register("torii", () -> new ToriiBlock(
            BlockBehaviour.Properties.of()
                .strength(2.0f, 6.0f)
                .sound(SoundType.WOOD)
                .noOcclusion()
        ));

    public static final DeferredHolder<Block, ItomakiBlock> ITOMAKI =
        register("itomaki", () -> new ItomakiBlock(
            BlockBehaviour.Properties.of()
                .strength(2.0f, 6.0f)
                .sound(SoundType.WOOD)
                .noOcclusion()
        ));

    public static final DeferredHolder<Block, DonationBoxBlock> DONATION_BOX =
        register("donation_box", () -> new DonationBoxBlock(
            BlockBehaviour.Properties.of()
                .strength(2.5f, 8.0f)
                .sound(SoundType.WOOD)
        ));

    /** Placed only via ShimenawaItem — no standalone BlockItem. */
    public static final DeferredHolder<Block, ShinbokuBlock> SHINBOKU =
        registerNoItem("shinboku", () -> new ShinbokuBlock(
            BlockBehaviour.Properties.of()
                .strength(3.0f, 9.0f)
                .sound(SoundType.WOOD)
                .lightLevel(state -> 3)
        ));

    public static final DeferredHolder<Block, LoomBlock> LOOM =
        register("loom", () -> new LoomBlock(
            BlockBehaviour.Properties.of()
                .strength(2.5f, 6.0f)
                .sound(SoundType.WOOD)
                .noOcclusion()
        ));

    /** Placed only via SilkwormItem — no BlockItem. */
    public static final DeferredHolder<Block, SilkwormBlock> SILKWORM =
        registerNoItem("silkworm", () -> new SilkwormBlock(
            BlockBehaviour.Properties.of()
                .strength(0.1f)
                .sound(SoundType.WOOL)
                .noOcclusion()
                .noCollission()
                .randomTicks()
        ));

    public static final DeferredHolder<Block, TatamiBlock> TATAMI =
        register("tatami", () -> new TatamiBlock(
            BlockBehaviour.Properties.of()
                .strength(0.6f, 0.6f)
                .sound(SoundType.GRASS)
        ));

    public static final DeferredHolder<Block, HikidashiBlock> HIKIDASHI =
        register("hikidashi", () -> new HikidashiBlock(
            BlockBehaviour.Properties.of()
                .strength(2.0f, 6.0f)
                .sound(SoundType.WOOD)
                .noOcclusion()
        ));

    private static <T extends Block> DeferredHolder<Block, T> register(String name, Supplier<T> block) {
        DeferredHolder<Block, T> holder = BLOCKS.register(name, block);
        BLOCK_ITEMS.register(name, () -> new MusubiBlockItem(holder.get(), new Item.Properties()));
        return holder;
    }

    private static <T extends Block> DeferredHolder<Block, T> registerNoItem(String name, Supplier<T> block) {
        return BLOCKS.register(name, block);
    }
}
