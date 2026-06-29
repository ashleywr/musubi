package com.sanhiruzu.musubi.blockentity;

import com.sanhiruzu.musubi.Musubi;
import com.sanhiruzu.musubi.block.MusubiBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MusubiBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Musubi.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpoolRackBlockEntity>> SPOOL_RACK =
        BLOCK_ENTITIES.register("spool_rack", () -> BlockEntityType.Builder
            .of(SpoolRackBlockEntity::new, MusubiBlocks.SPOOL_RACK.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MarudaiBlockEntity>> MARUDAI =
        BLOCK_ENTITIES.register("marudai", () -> BlockEntityType.Builder
            .of(MarudaiBlockEntity::new, MusubiBlocks.MARUDAI.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpinningWheelBlockEntity>> SPINNING_WHEEL =
        BLOCK_ENTITIES.register("spinning_wheel", () -> BlockEntityType.Builder
            .of(SpinningWheelBlockEntity::new, MusubiBlocks.SPINNING_WHEEL.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShrineStoneBlockEntity>> SHRINE_STONE =
        BLOCK_ENTITIES.register("shrine_stone", () -> BlockEntityType.Builder
            .of(ShrineStoneBlockEntity::new, MusubiBlocks.SHRINE_STONE.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ItomakiBlockEntity>> ITOMAKI =
        BLOCK_ENTITIES.register("itomaki", () -> BlockEntityType.Builder
            .of(ItomakiBlockEntity::new, MusubiBlocks.ITOMAKI.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DonationBoxBlockEntity>> DONATION_BOX =
        BLOCK_ENTITIES.register("donation_box", () -> BlockEntityType.Builder
            .of(DonationBoxBlockEntity::new, MusubiBlocks.DONATION_BOX.get())
            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShinbokuBlockEntity>> SHINBOKU =
        BLOCK_ENTITIES.register("shinboku", () -> BlockEntityType.Builder
            .of(ShinbokuBlockEntity::new, MusubiBlocks.SHINBOKU.get())
            .build(null));
}
