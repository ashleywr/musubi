package com.sanhiruzu.musubi.block;

import com.sanhiruzu.musubi.item.MusubiItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SilkwormBlock extends Block {

    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 2);

    /** Chance per randomTick to advance one growth stage. */
    private static final float GROW_CHANCE = 0.15f;

    private static final VoxelShape SHAPE_0 = Block.box(6, 12, 6, 10, 16, 10);
    private static final VoxelShape SHAPE_1 = Block.box(5,  9, 5, 11, 16, 11);
    private static final VoxelShape SHAPE_2 = Block.box(4,  6, 4, 12, 16, 12);

    public SilkwormBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AGE)) {
            case 0  -> SHAPE_0;
            case 1  -> SHAPE_1;
            default -> SHAPE_2;
        };
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.above()).is(Blocks.CHERRY_LEAVES);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block neighborBlock, BlockPos neighborPos, boolean isMoving) {
        if (!state.canSurvive(level, pos)) {
            // Drop item based on growth stage, then remove.
            ItemStack drop = state.getValue(AGE) >= 2
                ? new ItemStack(MusubiItems.SILKWORM_COCOON.get(), 2)
                : new ItemStack(MusubiItems.SILKWORM.get());
            Block.popResource(level, pos, drop);
            level.removeBlock(pos, false);
        }
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age < 2 && random.nextFloat() < GROW_CHANCE) {
            level.setBlock(pos, state.setValue(AGE, age + 1), UPDATE_CLIENTS);
        }
    }
}
