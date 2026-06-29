package com.sanhiruzu.musubi.block;

import com.mojang.serialization.MapCodec;
import com.sanhiruzu.musubi.blockentity.SpoolRackBlockEntity;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.item.SkeinItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Stores up to four item types (SLOTS × SLOT_CAPACITY items each).
 *
 * FACING is the direction the rack's display face points — set to face the player
 * on placement so the spool icons (rendered by a BER after the model is built) are
 * always visible from the natural viewing angle.
 *
 * Right-click with a skein   → deposit skein contents; skein stays in hand with
 *                              remainder if the rack fills for some variants.
 * Right-click with empty hand → extract one stack (64) from the first non-empty slot.
 * Shift + right-click         → extract all items from all slots.
 */
public class SpoolRackBlock extends BaseEntityBlock {

    public static final MapCodec<SpoolRackBlock> CODEC = simpleCodec(SpoolRackBlock::new);
    public static final DirectionProperty FACING  = BlockStateProperties.HORIZONTAL_FACING;
    /** True when the rack is mounted on the underside of a block (pegs hang downward). */
    public static final BooleanProperty   HANGING = BooleanProperty.create("hanging");

    @Override
    public MapCodec<SpoolRackBlock> codec() {
        return CODEC;
    }

    public SpoolRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(HANGING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HANGING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean hanging = context.getClickedFace() == Direction.DOWN;
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(HANGING, hanging);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HANGING)) {
            return Block.canSupportCenter(level, pos.above(), Direction.DOWN);
        }
        Direction behind = state.getValue(FACING).getOpposite();
        return Block.canSupportCenter(level, pos.relative(behind), behind.getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpoolRackBlockEntity(pos, state);
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

        if (!(stack.getItem() instanceof SkeinItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        SpoolRackBlockEntity be = getBlockEntity(level, pos);
        if (be == null) return ItemInteractionResult.FAIL;

        SkeinContents contents = stack.get(MusubiDataComponents.SKEIN_CONTENTS);
        if (contents == null || contents.count() == 0) return ItemInteractionResult.FAIL;

        SkeinContents remainder = be.depositSkein(contents);
        if (remainder == null) {
            stack.shrink(1);
        } else {
            stack.set(MusubiDataComponents.SKEIN_CONTENTS, remainder);
        }
        level.sendBlockUpdated(pos, state, state, 3);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {

        if (level.isClientSide) return InteractionResult.SUCCESS;

        SpoolRackBlockEntity be = getBlockEntity(level, pos);
        if (be == null) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            be.extractAll(player);
        } else {
            be.extractOne(player);
        }
        level.sendBlockUpdated(pos, state, state, 3);
        return InteractionResult.SUCCESS;
    }

    // ── Break behaviour ───────────────────────────────────────────────────────

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            SpoolRackBlockEntity be = getBlockEntity(level, pos);
            if (be != null) be.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    private static SpoolRackBlockEntity getBlockEntity(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof SpoolRackBlockEntity rack ? rack : null;
    }
}
