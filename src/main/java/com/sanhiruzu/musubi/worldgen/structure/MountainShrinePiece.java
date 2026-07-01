package com.sanhiruzu.musubi.worldgen.structure;

import com.sanhiruzu.musubi.block.DonationBoxBlock;
import com.sanhiruzu.musubi.block.MusubiBlocks;
import com.sanhiruzu.musubi.block.ToriiBlock;
import com.sanhiruzu.musubi.blockentity.ShinbokuBlockEntity;
import com.sanhiruzu.musubi.worldgen.MusubiStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public class MountainShrinePiece extends StructurePiece {

    private static final int WIDTH = 17;
    private static final int HEIGHT = 15;
    private static final int DEPTH = 21;

    private static final int PATH_MIN_X = 6;
    private static final int PATH_MAX_X = 10;
    private static final int TERRACE_MIN_X = 4;
    private static final int TERRACE_MAX_X = 12;
    private static final int SHRINE_MIN_X = 5;
    private static final int SHRINE_MAX_X = 11;
    private static final int SHRINE_MIN_Z = 13;
    private static final int SHRINE_MAX_Z = 17;

    private static final BlockState MOSSY_COBBLESTONE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    private static final BlockState COBBLESTONE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState STONE_BRICKS = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState MOSSY_STONE_BRICKS = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    private static final BlockState MOSSY_COBBLESTONE_STAIRS_SOUTH = Blocks.MOSSY_COBBLESTONE_STAIRS.defaultBlockState()
        .setValue(StairBlock.FACING, Direction.SOUTH);
    private static final BlockState OAK_LOG = Blocks.OAK_LOG.defaultBlockState();
    private static final BlockState OAK_LEAVES = Blocks.OAK_LEAVES.defaultBlockState();
    private static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState MOSS_BLOCK = Blocks.MOSS_BLOCK.defaultBlockState();
    private static final BlockState MOSS_CARPET = Blocks.MOSS_CARPET.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DARK_OAK_LOG = Blocks.DARK_OAK_LOG.defaultBlockState();
    private static final BlockState DARK_OAK_PLANKS = Blocks.DARK_OAK_PLANKS.defaultBlockState();
    private static final BlockState DARK_OAK_SLAB = Blocks.DARK_OAK_SLAB.defaultBlockState();
    private static final BlockState DARK_OAK_STAIRS_NORTH = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
    private static final BlockState DARK_OAK_STAIRS_SOUTH = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
    private static final BlockState DARK_OAK_STAIRS_EAST = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
    private static final BlockState DARK_OAK_STAIRS_WEST = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
    private static final BlockState DARK_OAK_FENCE = Blocks.DARK_OAK_FENCE.defaultBlockState();
    private static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState();
    private static final BlockState CHISELED_STONE_BRICKS = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    private static final BlockState SHRINE_STONE = MusubiBlocks.SHRINE_STONE.get().defaultBlockState();
    private static final BlockState SHINBOKU = MusubiBlocks.SHINBOKU.get().defaultBlockState();
    private static final BlockState TORII = MusubiBlocks.TORII.get().defaultBlockState().setValue(ToriiBlock.FACING, Direction.NORTH);
    private static final BlockState DONATION_BOX_NORTH = MusubiBlocks.DONATION_BOX.get().defaultBlockState().setValue(DonationBoxBlock.FACING, Direction.NORTH);

    public MountainShrinePiece(CompoundTag tag) {
        super(MusubiStructures.MOUNTAIN_SHRINE_PIECE.get(), tag);
    }

    public MountainShrinePiece(BlockPos center, Direction orientation) {
        super(
            MusubiStructures.MOUNTAIN_SHRINE_PIECE.get(),
            0,
            makeBoundingBox(center.getX() - WIDTH / 2, center.getY(), center.getZ() - DEPTH / 2, orientation, WIDTH, HEIGHT, DEPTH)
        );
        this.setOrientation(orientation);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
    }

    @Override
    public void postProcess(
        WorldGenLevel level,
        StructureManager structureManager,
        ChunkGenerator generator,
        RandomSource random,
        BoundingBox box,
        ChunkPos chunkPos,
        BlockPos pos
    ) {
        buildStairApproach(level, box);
        buildTerrace(level, box, random);
        buildTorii(level, box);
        decorateTerrace(level, box, random);
        buildTrees(level, box, random);
        buildShrinePavilion(level, box, random);
    }

    private void buildStairApproach(WorldGenLevel level, BoundingBox box) {
        for (int z = 0; z <= 5; z++) {
            int y = z;
            for (int x = PATH_MIN_X; x <= PATH_MAX_X; x++) {
                placeSupported(level, box, COBBLESTONE, MOSSY_COBBLESTONE_STAIRS_SOUTH, x, y, z);
            }
        }
    }

    private void buildTerrace(WorldGenLevel level, BoundingBox box, RandomSource random) {
        for (int z = 6; z <= 12; z++) {
            for (int x = TERRACE_MIN_X; x <= TERRACE_MAX_X; x++) {
                BlockState floor = random.nextFloat() < 0.65F ? STONE_BRICKS : MOSSY_STONE_BRICKS;
                placeSupported(level, box, MOSSY_COBBLESTONE, floor, x, 6, z);
            }
        }
    }

    private void buildTorii(WorldGenLevel level, BoundingBox box) {
        placeBlock(level, TORII, 8, 6, 6, box);
        placeLanternStand(level, box, 4, 7, 6);
        placeLanternStand(level, box, 12, 7, 6);
    }

    private void decorateTerrace(WorldGenLevel level, BoundingBox box, RandomSource random) {
        for (int x = 4; x <= 12; x++) {
            for (int z = 7; z <= 12; z++) {
                if (random.nextFloat() < 0.18F) {
                    placeBlock(level, MOSS_CARPET, x, 7, z, box);
                }
            }
        }
    }

    private void buildTrees(WorldGenLevel level, BoundingBox box, RandomSource random) {
        buildSacredTree(level, box, random, 14, 9, 6);
        buildSacredStone(level, box, random, 2, 9, 6);
    }

    private void buildSacredTree(WorldGenLevel level, BoundingBox box, RandomSource random, int x, int z, int floorY) {
        placeSupported(level, box, COARSE_DIRT, MOSS_BLOCK, x, floorY + 1, z);
        placeSupported(level, box, COARSE_DIRT, OAK_LOG, x, floorY + 2, z);
        placeSupported(level, box, COARSE_DIRT, SHINBOKU, x, floorY + 3, z);
        wrapShinboku(level, x, floorY + 3, z, OAK_LOG);
        placeSupported(level, box, COARSE_DIRT, OAK_LOG, x, floorY + 4, z);
        generateUpperHalfSphere(level, box, x - 2, floorY + 4, z - 2, x + 2, floorY + 6, z + 2, OAK_LEAVES, false);

        if (random.nextBoolean()) {
            placeBlock(level, MOSS_CARPET, x + 1, floorY + 1, z, box);
        }
        if (random.nextBoolean()) {
            placeBlock(level, MOSS_CARPET, x - 1, floorY + 1, z, box);
        }
    }

    private void buildSacredStone(WorldGenLevel level, BoundingBox box, RandomSource random, int x, int z, int floorY) {
        placeSupported(level, box, COARSE_DIRT, MOSS_BLOCK, x, floorY + 1, z);
        placeBlock(level, STONE, x, floorY + 2, z, box);
        placeBlock(level, SHINBOKU, x, floorY + 3, z, box);
        wrapShinboku(level, x, floorY + 3, z, STONE);
    }

    private void buildShrinePavilion(WorldGenLevel level, BoundingBox box, RandomSource random) {
        for (int z = SHRINE_MIN_Z; z <= SHRINE_MAX_Z; z++) {
            for (int x = SHRINE_MIN_X; x <= SHRINE_MAX_X; x++) {
                placeSupported(level, box, STONE_BRICKS, DARK_OAK_PLANKS, x, 7, z);
            }
        }

        buildPillar(level, box, 5, 13, 8, 10);
        buildPillar(level, box, 11, 13, 8, 10);
        buildPillar(level, box, 5, 17, 8, 10);
        buildPillar(level, box, 11, 17, 8, 10);

        placeBlock(level, DONATION_BOX_NORTH, 8, 8, 13, box);
        placeBlock(level, CHISELED_STONE_BRICKS, 8, 8, 15, box);
        placeBlock(level, SHRINE_STONE, 8, 9, 15, box);

        placeLanternStand(level, box, 6, 8, 14);
        placeLanternStand(level, box, 10, 8, 14);
        placeLanternStand(level, box, 6, 8, 16);
        placeLanternStand(level, box, 10, 8, 16);

        placeRoofRing(level, box, 5, 11, 13, 17, 11, DARK_OAK_SLAB);
        placeRoofRing(level, box, 6, 10, 14, 16, 12, DARK_OAK_SLAB);
        for (int x = 7; x <= 9; x++) {
            placeBlock(level, DARK_OAK_LOG.setValue(BlockStateProperties.AXIS, Axis.X), x, 13, 15, box);
        }

        if (random.nextBoolean()) {
            placeBlock(level, MOSS_CARPET, 8, 8, 15, box);
        }
    }

    private void buildPillar(WorldGenLevel level, BoundingBox box, int x, int z, int minY, int maxY) {
        for (int y = minY; y <= maxY; y++) {
            placeBlock(level, DARK_OAK_LOG, x, y, z, box);
        }
    }

    private void placeRoofRing(WorldGenLevel level, BoundingBox box, int xMin, int xMax, int zMin, int zMax, int y, BlockState fill) {
        for (int x = xMin; x <= xMax; x++) {
            placeBlock(level, roofStairs(Direction.NORTH), x, y, zMin, box);
            placeBlock(level, roofStairs(Direction.SOUTH), x, y, zMax, box);
        }
        for (int z = zMin + 1; z < zMax; z++) {
            placeBlock(level, roofStairs(Direction.WEST), xMin, y, z, box);
            placeBlock(level, roofStairs(Direction.EAST), xMax, y, z, box);
        }
        for (int x = xMin + 1; x < xMax; x++) {
            for (int z = zMin + 1; z < zMax; z++) {
                placeBlock(level, fill, x, y, z, box);
            }
        }
    }

    private static BlockState roofStairs(Direction direction) {
        return switch (direction) {
            case NORTH -> DARK_OAK_STAIRS_NORTH;
            case SOUTH -> DARK_OAK_STAIRS_SOUTH;
            case EAST -> DARK_OAK_STAIRS_EAST;
            case WEST -> DARK_OAK_STAIRS_WEST;
            default -> DARK_OAK_STAIRS_SOUTH;
        };
    }

    private void placeLanternStand(WorldGenLevel level, BoundingBox box, int x, int y, int z) {
        placeSupported(level, box, STONE_BRICKS, DARK_OAK_FENCE, x, y, z);
        placeBlock(level, LANTERN, x, y + 1, z, box);
    }

    private void wrapShinboku(WorldGenLevel level, int x, int y, int z, BlockState wrappedState) {
        BlockPos shinbokuPos = this.getWorldPos(x, y, z);
        if (level.getBlockEntity(shinbokuPos) instanceof ShinbokuBlockEntity shinbokuBlockEntity) {
            shinbokuBlockEntity.setWrappedBlock(wrappedState);
        }
    }

    private void placeSupported(WorldGenLevel level, BoundingBox box, BlockState support, BlockState state, int x, int y, int z) {
        fillColumnDown(level, support, x, y, z, box);
        placeBlock(level, state, x, y, z, box);
    }
}
