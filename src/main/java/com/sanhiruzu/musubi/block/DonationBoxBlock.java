package com.sanhiruzu.musubi.block;

import com.mojang.serialization.MapCodec;
import com.sanhiruzu.musubi.blockentity.DonationBoxBlockEntity;
import com.sanhiruzu.musubi.blockentity.MusubiBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
 * The Saisen-bako (賽銭箱) — a wooden offering box.
 *
 * Right-click with item  → donate (item consumed, REI value queued).
 * Right-click empty      → bow once (then once more within 5 seconds to complete the ritual).
 */
public class DonationBoxBlock extends BaseEntityBlock {

    public static final MapCodec<DonationBoxBlock> CODEC = simpleCodec(DonationBoxBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    @Override public MapCodec<DonationBoxBlock> codec() { return CODEC; }

    public DonationBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof DonationBoxBlockEntity be) {
            be.donate(stack, player);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (level.getBlockEntity(pos) instanceof DonationBoxBlockEntity be) {
            DonationBoxBlockEntity.BowResult result = be.bow(player);
            if (result == DonationBoxBlockEntity.BowResult.RITUAL_COMPLETE) {
                playRitualComplete(level, pos);
            } else {
                // First bow — soft chime
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(),
                    SoundSource.BLOCKS, 0.8f, 1.0f);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private void playRitualComplete(Level level, BlockPos pos) {
        // Two claps (slight pitch shift) then a bell.
        level.playSound(null, pos, SoundEvents.ARROW_HIT_PLAYER,
            SoundSource.BLOCKS, 0.6f, 1.4f);
        level.playSound(null, pos, SoundEvents.ARROW_HIT_PLAYER,
            SoundSource.BLOCKS, 0.6f, 1.6f);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(),
            SoundSource.BLOCKS, 1.0f, 1.0f);
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                30, 0.3, 0.4, 0.3, 0.2);
        }
    }

    // ── Block entity ─────────────────────────────────────────────────────────

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DonationBoxBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
            : createTickerHelper(type, MusubiBlockEntities.DONATION_BOX.get(),
                DonationBoxBlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
