package com.sanhiruzu.musubi.worldgen;

import com.sanhiruzu.musubi.Musubi;
import com.sanhiruzu.musubi.worldgen.structure.MountainShrinePiece;
import com.sanhiruzu.musubi.worldgen.structure.MountainShrineStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MusubiStructures {

    private static final StructureType<MountainShrineStructure> MOUNTAIN_SHRINE_TYPE = () -> MountainShrineStructure.CODEC;
    private static final StructurePieceType.ContextlessType MOUNTAIN_SHRINE_PIECE_TYPE = MountainShrinePiece::new;

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
        DeferredRegister.create(Registries.STRUCTURE_TYPE, Musubi.MOD_ID);

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
        DeferredRegister.create(Registries.STRUCTURE_PIECE, Musubi.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<?>> MOUNTAIN_SHRINE =
        STRUCTURE_TYPES.register("mountain_shrine", () -> MOUNTAIN_SHRINE_TYPE);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> MOUNTAIN_SHRINE_PIECE =
        STRUCTURE_PIECES.register("mountain_shrine_piece", () -> MOUNTAIN_SHRINE_PIECE_TYPE);

    private MusubiStructures() {
    }
}
