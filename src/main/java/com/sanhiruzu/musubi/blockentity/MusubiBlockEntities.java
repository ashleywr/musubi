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

    // Pending:
    // public static final DeferredHolder<...> SPINNING_WHEEL = ...
}
