package com.sanhiruzu.musubi.block;

import com.mojang.serialization.MapCodec;
import com.sanhiruzu.musubi.blockentity.LoomBlockEntity;
import com.sanhiruzu.musubi.blockentity.MusubiBlockEntities;
import com.sanhiruzu.musubi.item.WeavingPatternItem;
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
 * The loom — an REI-powered autocrafter.
 *
 * Right-click with a Weaving Pattern → install it (the previous pattern, if any,
 * is returned). The loom then repeatedly crafts the pattern's target, pulling
 * ingredients from adjacent containers and spending REI per craft.
 *
 * Right-click with empty hand when output is present → retrieve the output.
 * Right-click with empty hand otherwise → retrieve the installed pattern.
 */
public class LoomBlock extends BaseEntityBlock {

    public static final MapCodec<LoomBlock> CODEC = simpleCodec(LoomBlock::new);
    public static final DirectionProperty FACING  = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty   WEAVING = BooleanProperty.create("weaving");

    @Override
    public MapCodec<LoomBlock> codec() { return CODEC; }

    public LoomBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(WEAVING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WEAVING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(WEAVING, false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LoomBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
            : createTickerHelper(type, MusubiBlockEntities.LOOM.get(), LoomBlockEntity::serverTick);
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

        if (stack.getItem() instanceof WeavingPatternItem) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            LoomBlockEntity be = getBlockEntity(level, pos);
            if (be == null) return ItemInteractionResult.FAIL;
            ItemStack previous = be.installPattern(stack, level, pos, state);
            PlayerInventoryUtil.giveDirect(player, previous);
            return ItemInteractionResult.CONSUME;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {

        if (level.isClientSide) return InteractionResult.SUCCESS;

        LoomBlockEntity be = getBlockEntity(level, pos);
        if (be == null) return InteractionResult.PASS;

        // Retrieve output first; if empty, retrieve the installed pattern
        if (!be.getOutput().isEmpty()) {
            ItemStack out = be.retrieveOutput(level, pos, state);
            PlayerInventoryUtil.giveDirect(player, out);
            return InteractionResult.SUCCESS;
        }

        if (!be.getPattern().isEmpty()) {
            ItemStack pattern = be.retrievePattern(level, pos, state);
            PlayerInventoryUtil.giveDirect(player, pattern);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    // ── Break ─────────────────────────────────────────────────────────────────

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            LoomBlockEntity be = getBlockEntity(level, pos);
            if (be != null) be.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    private static LoomBlockEntity getBlockEntity(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof LoomBlockEntity loom ? loom : null;
    }
}
