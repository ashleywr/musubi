package com.sanhiruzu.musubi.block;

import com.mojang.serialization.MapCodec;
import com.sanhiruzu.musubi.blockentity.MusubiBlockEntities;
import com.sanhiruzu.musubi.blockentity.ShinbokuBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The Shinboku (神木) — a natural object consecrated by wrapping it in Shimenawa.
 *
 * The visual appearance is a placeholder; the BER layer will eventually render
 * the original wrapped block with the Shimenawa rope overlay on top.
 *
 * On removal: drops the wrapped block's item + the Shimenawa rope.
 */
public class ShinbokuBlock extends BaseEntityBlock {

    public static final MapCodec<ShinbokuBlock> CODEC = simpleCodec(ShinbokuBlock::new);

    @Override public MapCodec<ShinbokuBlock> codec() { return CODEC; }

    public ShinbokuBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShinbokuBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
            : createTickerHelper(type, MusubiBlockEntities.SHINBOKU.get(),
                ShinbokuBlockEntity::serverTick);
    }

    /** Drop the wrapped block and the Shimenawa rope. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
            BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ShinbokuBlockEntity be) {
                be.dropContents(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
