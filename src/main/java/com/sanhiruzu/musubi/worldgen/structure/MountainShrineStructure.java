package com.sanhiruzu.musubi.worldgen.structure;

import com.mojang.serialization.MapCodec;
import com.sanhiruzu.musubi.worldgen.MusubiStructures;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public class MountainShrineStructure extends Structure {

    public static final MapCodec<MountainShrineStructure> CODEC = simpleCodec(MountainShrineStructure::new);

    public MountainShrineStructure(StructureSettings settings) {
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
        Direction direction = Direction.from2DDataValue(context.random().nextInt(4));

        return Optional.of(new GenerationStub(center, builder ->
            builder.addPiece(new MountainShrinePiece(center, direction))));
    }

    @Override
    public StructureType<?> type() {
        return MusubiStructures.MOUNTAIN_SHRINE.get();
    }
}
