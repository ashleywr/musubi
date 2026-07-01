package com.sanhiruzu.musubi.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class TatamiBlock extends Block {

    public static final MapCodec<TatamiBlock> CODEC = simpleCodec(TatamiBlock::new);

    @Override
    public MapCodec<TatamiBlock> codec() {
        return CODEC;
    }

    public TatamiBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.AXIS);
    }
}
