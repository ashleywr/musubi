package com.sanhiruzu.musubi.worldgen.structure;

import com.mojang.serialization.MapCodec;
import com.sanhiruzu.musubi.worldgen.MusubiStructures;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public class ItomakiCraterStructure extends Structure {

    public static final MapCodec<ItomakiCraterStructure> CODEC = simpleCodec(ItomakiCraterStructure::new);

    public ItomakiCraterStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();
        int y = context.chunkGenerator().getFirstOccupiedHeight(
            x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()
        );
        BlockPos center = new BlockPos(x, y, z);

        return Optional.of(new GenerationStub(center, builder ->
            builder.addPiece(new ItomakiCraterPiece(center))));
    }

    @Override
    public StructureType<?> type() {
        return MusubiStructures.ITOMAKI_CRATER.get();
    }
}
