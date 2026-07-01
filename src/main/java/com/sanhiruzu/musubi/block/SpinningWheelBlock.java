package com.sanhiruzu.musubi.block;

import com.mojang.serialization.MapCodec;
import com.sanhiruzu.musubi.blockentity.MusubiBlockEntities;
import com.sanhiruzu.musubi.blockentity.SpinningWheelBlockEntity;
import com.sanhiruzu.musubi.item.MusubiItems;
import com.sanhiruzu.musubi.util.PlayerInventoryUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Extracts items from a loaded Silk Skein and ejects them in front of the block, one
 * batch of up to 64 every TICKS_PER_EXTRACT ticks.
 *
 * Right-click with a Silk Skein   → load the skein and start spinning.
 * Right-click with empty hand     → retrieve the loaded skein (stops spinning).
 * On break                        → loaded skein is dropped as an item entity.
 */
public class SpinningWheelBlock extends BaseEntityBlock {

    public static final MapCodec<SpinningWheelBlock> CODEC = simpleCodec(SpinningWheelBlock::new);
    public static final DirectionProperty FACING  = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty   SPINNING = BooleanProperty.create("spinning");

    @Override
    public MapCodec<SpinningWheelBlock> codec() {
        return CODEC;
    }

    public SpinningWheelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(SPINNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SPINNING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(SPINNING, false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpinningWheelBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
            : createTickerHelper(type, MusubiBlockEntities.SPINNING_WHEEL.get(),
                SpinningWheelBlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand,
            BlockHitResult hit) {

        if (stack.is(MusubiItems.SKEIN.get())) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            SpinningWheelBlockEntity be = getBlockEntity(level, pos);
            if (be == null) return ItemInteractionResult.FAIL;
            if (!be.getSkein().isEmpty() || !be.getRawInput().isEmpty()) return ItemInteractionResult.FAIL;

            be.loadSkein(stack.copyWithCount(1), level, pos, state);
            stack.shrink(1);
            return ItemInteractionResult.CONSUME;
        }

        if (stack.is(MusubiItems.SILK_THREAD.get())) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            SpinningWheelBlockEntity be = getBlockEntity(level, pos);
            if (be == null) return ItemInteractionResult.FAIL;
            if (!be.getSkein().isEmpty()) return ItemInteractionResult.FAIL; // skein has priority

            be.loadRawInput(stack, level, pos, state);
            return ItemInteractionResult.CONSUME;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {

        if (level.isClientSide) return InteractionResult.SUCCESS;

        SpinningWheelBlockEntity be = getBlockEntity(level, pos);
        if (be == null) return InteractionResult.PASS;

        if (!be.getSkein().isEmpty()) {
            ItemStack retrieved = be.retrieveSkein(level, pos, state);
            PlayerInventoryUtil.giveDirect(player, retrieved);
            return InteractionResult.SUCCESS;
        }

        if (!be.getRawInput().isEmpty()) {
            ItemStack retrieved = be.retrieveRawInput(level, pos, state);
            PlayerInventoryUtil.giveDirect(player, retrieved);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // ── Break behaviour ───────────────────────────────────────────────────────

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            SpinningWheelBlockEntity be = getBlockEntity(level, pos);
            if (be != null) be.dropSkein(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    private static SpinningWheelBlockEntity getBlockEntity(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof SpinningWheelBlockEntity wheel ? wheel : null;
    }
}
