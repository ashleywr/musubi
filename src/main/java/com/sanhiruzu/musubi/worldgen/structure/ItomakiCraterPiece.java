package com.sanhiruzu.musubi.worldgen.structure;

import com.sanhiruzu.musubi.block.MusubiBlocks;
import com.sanhiruzu.musubi.worldgen.MusubiStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

public class ItomakiCraterPiece extends StructurePiece {

    private static final int CRATER_RADIUS = 64;
    private static final int MAX_DEPTH = 32;
    private static final BlockState SHINBOKU = MusubiBlocks.SHINBOKU.get().defaultBlockState();
    private static final BlockState SHRINE_STONE = MusubiBlocks.SHRINE_STONE.get().defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState DIORITE = Blocks.DIORITE.defaultBlockState();
    private static final BlockState GRANITE = Blocks.GRANITE.defaultBlockState();
    private static final BlockState MOSS_BLOCK = Blocks.MOSS_BLOCK.defaultBlockState();
    private static final BlockState MOSS_CARPET = Blocks.MOSS_CARPET.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState OAK_LOG = Blocks.OAK_LOG.defaultBlockState();
    private static final BlockState OAK_LEAVES = Blocks.OAK_LEAVES.defaultBlockState();

    public ItomakiCraterPiece(BlockPos center) {
        super(
            MusubiStructures.ITOMAKI_CRATER_PIECE.get(),
            0,
            makeBoundingBox(
                center.getX() - CRATER_RADIUS,
                center.getY() - MAX_DEPTH,
                center.getZ() - CRATER_RADIUS,
                null,
                CRATER_RADIUS * 2,
                MAX_DEPTH + 4,
                CRATER_RADIUS * 2
            )
        );
    }

    public ItomakiCraterPiece(CompoundTag tag) {
        super(MusubiStructures.ITOMAKI_CRATER_PIECE.get(), tag);
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
        BlockPos centerWorldPos = this.getWorldPos(CRATER_RADIUS, 0, CRATER_RADIUS);
        int centerX = centerWorldPos.getX();
        int centerY = centerWorldPos.getY();
        int centerZ = centerWorldPos.getZ();

        // Generate crater depression
        for (int x = -CRATER_RADIUS; x <= CRATER_RADIUS; x++) {
            for (int z = -CRATER_RADIUS; z <= CRATER_RADIUS; z++) {
                double distFromCenter = Math.sqrt(x * x + z * z);

                if (distFromCenter <= CRATER_RADIUS) {
                    // Calculate depth based on distance from center (parabolic crater shape)
                    double depthFactor = 1.0 - (distFromCenter / CRATER_RADIUS);
                    int depth = (int) (MAX_DEPTH * depthFactor * depthFactor);

                    // Fill down from surface to create crater
                    for (int y = -depth; y <= 0; y++) {
                        int worldX = centerX + x;
                        int worldY = centerY + y;
                        int worldZ = centerZ + z;

                        BlockPos checkPos = new BlockPos(worldX, worldY, worldZ);
                        if (!box.isInside(checkPos)) continue;

                        if (y == 0) {
                            // Surface layer - vary stone types and add moss
                            BlockState stoneType = switch (random.nextInt(4)) {
                                case 0 -> GRANITE;
                                case 1 -> DIORITE;
                                case 2 -> ANDESITE;
                                default -> STONE;
                            };
                            level.setBlock(checkPos, stoneType, 2);

                            // Occasional moss carpet
                            if (random.nextFloat() < 0.15F && distFromCenter > 8) {
                                BlockPos abovePos = checkPos.above();
                                if (box.isInside(abovePos)) {
                                    level.setBlock(abovePos, MOSS_CARPET, 2);
                                }
                            }
                        } else if (y > -depth) {
                            // Interior layers
                            BlockState interior = switch (random.nextInt(10)) {
                                case 0, 1 -> MOSS_BLOCK;
                                case 2 -> DIORITE;
                                case 3 -> ANDESITE;
                                default -> STONE;
                            };
                            level.setBlock(checkPos, interior, 2);
                        }
                    }
                }
            }
        }

        // Place giant sacred tree at center
        placeSacredTree(level, box, random, centerX, centerY, centerZ);

        // Add water streams around the tree
        addWaterStreams(level, box, centerX, centerY, centerZ);

        // Add decorative rocks and vegetation around the crater rim
        addRimDecorations(level, box, random, centerX, centerY, centerZ);
    }

    private void placeSacredTree(WorldGenLevel level, BoundingBox box, RandomSource random, int centerX, int centerY, int centerZ) {
        // Base platform with moss
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.sqrt(x * x + z * z) <= 3.5) {
                    BlockPos pos = new BlockPos(centerX + x, centerY, centerZ + z);
                    if (box.isInside(pos)) {
                        level.setBlock(pos, MOSS_BLOCK, 2);
                    }
                }
            }
        }

        // Tree trunk - tall shinboku
        for (int y = 1; y <= 12; y++) {
            BlockPos trunkPos = new BlockPos(centerX, centerY + y, centerZ);
            if (box.isInside(trunkPos)) {
                level.setBlock(trunkPos, SHINBOKU, 2);
            }
        }

        // Add oak log structure
        for (int y = 3; y <= 10; y++) {
            BlockPos logPos = new BlockPos(centerX, centerY + y, centerZ);
            if (box.isInside(logPos) && y > 12) {
                level.setBlock(logPos, OAK_LOG, 2);
            }
        }

        // Large canopy - expanding sphere of leaves
        for (int y = 8; y <= 16; y++) {
            int radiusAtHeight = 2 + (y - 8) / 2;
            for (int x = -radiusAtHeight; x <= radiusAtHeight; x++) {
                for (int z = -radiusAtHeight; z <= radiusAtHeight; z++) {
                    if (Math.sqrt(x * x + z * z) <= radiusAtHeight + 0.5) {
                        BlockPos leafPos = new BlockPos(centerX + x, centerY + y, centerZ + z);
                        if (box.isInside(leafPos) && random.nextFloat() < 0.8F) {
                            level.setBlock(leafPos, OAK_LEAVES, 2);
                        }
                    }
                }
            }
        }

        // Shrine stone as offering at tree base
        BlockPos shrinePos = new BlockPos(centerX, centerY + 1, centerZ + 3);
        if (box.isInside(shrinePos)) {
            level.setBlock(shrinePos, SHRINE_STONE, 2);
        }
    }

    private void addWaterStreams(WorldGenLevel level, BoundingBox box, int centerX, int centerY, int centerZ) {
        // Create water channels radiating from tree in cardinal directions
        int[] directions = {0, 90, 180, 270};
        for (int dir : directions) {
            double rad = Math.toRadians(dir);
            for (int dist = 5; dist <= 24; dist++) {
                int streamX = centerX + (int) (Math.cos(rad) * dist);
                int streamZ = centerZ + (int) (Math.sin(rad) * dist);

                // Create shallow stream bed
                for (int offsetX = -1; offsetX <= 1; offsetX++) {
                    BlockPos streamPos = new BlockPos(streamX + offsetX, centerY - 1, streamZ);
                    if (box.isInside(streamPos)) {
                        level.setBlock(streamPos, MOSS_BLOCK, 2);
                    }

                    // Water channel
                    BlockPos waterPos = new BlockPos(streamX + offsetX, centerY, streamZ);
                    if (box.isInside(waterPos)) {
                        level.setBlock(waterPos, WATER, 2);
                    }
                }
            }
        }

        // Circular water moat around immediate tree area
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                double dist = Math.sqrt(x * x + z * z);
                if (dist >= 4.5 && dist <= 5.5) {
                    BlockPos moatPos = new BlockPos(centerX + x, centerY, centerZ + z);
                    if (box.isInside(moatPos)) {
                        level.setBlock(moatPos, WATER, 2);
                    }
                }
            }
        }
    }

    private void addRimDecorations(WorldGenLevel level, BoundingBox box, RandomSource random, int centerX, int centerY, int centerZ) {
        // Scatter rocks and vegetation around the crater rim - more for bigger crater
        for (int angle = 0; angle < 32; angle++) {
            double rad = angle * Math.PI / 16;
            int rimX = centerX + (int) (Math.cos(rad) * (CRATER_RADIUS - 6));
            int rimZ = centerZ + (int) (Math.sin(rad) * (CRATER_RADIUS - 6));

            // Find surface at this position
            int rimY = centerY;
            for (int y = centerY + MAX_DEPTH; y > centerY - MAX_DEPTH; y--) {
                BlockPos checkPos = new BlockPos(rimX, y, rimZ);
                if (box.isInside(checkPos) && !level.getBlockState(checkPos).isAir()) {
                    rimY = y + 1;
                    break;
                }
            }

            // Place decorative stones and occasional moss
            if (random.nextFloat() < 0.7F) {
                BlockPos rockPos = new BlockPos(rimX, rimY, rimZ);
                if (box.isInside(rockPos)) {
                    BlockState rock = switch (random.nextInt(4)) {
                        case 0 -> GRANITE;
                        case 1 -> DIORITE;
                        case 2 -> ANDESITE;
                        default -> MOSS_BLOCK;
                    };
                    level.setBlock(rockPos, rock, 2);
                }
            }

            // Moss carpet on rim
            if (random.nextFloat() < 0.3F) {
                BlockPos mossPos = new BlockPos(rimX, rimY + 1, rimZ);
                if (box.isInside(mossPos)) {
                    level.setBlock(mossPos, MOSS_CARPET, 2);
                }
            }
        }
    }
}
