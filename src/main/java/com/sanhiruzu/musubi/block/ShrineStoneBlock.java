package com.sanhiruzu.musubi.block;

import com.mojang.serialization.MapCodec;
import com.sanhiruzu.musubi.blockentity.MusubiBlockEntities;
import com.sanhiruzu.musubi.blockentity.ShrineStoneBlockEntity;
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
 * The Shrine Stone (御神体) — a sacred object that passively generates REI.
 *
 * Generates 3 REI/t base, plus 2 REI/t per occupied Spool Rack within
 * 5 blocks (capped at +10). Musubi rewards an ordered silk space.
 */
public class ShrineStoneBlock extends BaseEntityBlock {

    public static final MapCodec<ShrineStoneBlock> CODEC = simpleCodec(ShrineStoneBlock::new);

    @Override public MapCodec<ShrineStoneBlock> codec() { return CODEC; }

    public ShrineStoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShrineStoneBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
            : createTickerHelper(type, MusubiBlockEntities.SHRINE_STONE.get(),
                ShrineStoneBlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
