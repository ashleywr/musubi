package com.sanhiruzu.musubi.block;

import com.mojang.serialization.MapCodec;
import com.sanhiruzu.musubi.blockentity.HikidashiBlockEntity;
import com.sanhiruzu.musubi.blockentity.MusubiBlockEntities;
import com.sanhiruzu.musubi.item.MusubiItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The Hikidashi (引き出し) — a magical crane creature that displays and extracts
 * items from connected nearby Spool Racks.
 *
 * Right-click to open storage GUI, where you can search, sort by tier, and extract silk.
 */
public class HikidashiBlock extends BaseEntityBlock {

    public static final MapCodec<HikidashiBlock> CODEC = simpleCodec(HikidashiBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    @Override public MapCodec<HikidashiBlock> codec() { return CODEC; }

    public HikidashiBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (level.getBlockEntity(pos) instanceof HikidashiBlockEntity be) {
            be.openGui(player);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** Right-click with gohei to purify corruption. */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
            BlockHitResult hit) {
        if (stack.is(MusubiItems.GOHEI.get())) {
            if (level.isClientSide) return net.minecraft.world.ItemInteractionResult.SUCCESS;

            if (level.getBlockEntity(pos) instanceof HikidashiBlockEntity be) {
                if (be.getCorruption() > 0) {
                    be.purify();
                    level.playSound((Player)null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        net.minecraft.sounds.SoundEvents.RESPAWN_ANCHOR_DEPLETE, net.minecraft.sounds.SoundSource.BLOCKS,
                        1.0f, 1.2f);
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§d✦ §6Miasma cleansed by gohei §d✦"),
                        true);
                    return net.minecraft.world.ItemInteractionResult.SUCCESS;
                }
            }
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HikidashiBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
            : createTickerHelper(type, MusubiBlockEntities.HIKIDASHI.get(),
                HikidashiBlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
