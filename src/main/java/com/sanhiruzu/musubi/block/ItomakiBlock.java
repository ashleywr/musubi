package com.sanhiruzu.musubi.block;

import com.mojang.serialization.MapCodec;
import com.sanhiruzu.musubi.blockentity.ItomakiBlockEntity;
import com.sanhiruzu.musubi.blockentity.MusubiBlockEntities;
import com.sanhiruzu.musubi.item.TamaItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
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
 * The Itomaki (糸巻き) — a thread-spindle pedestal where a Tama can rest while
 * it unspools its contents onto nearby Spool Racks.
 *
 * Right-click with Tama      → place Tama on the pedestal, begin unspooling.
 * Right-click with empty hand → retrieve the Tama (pauses if still spooling).
 * Break the block             → Tama is dropped.
 */
public class ItomakiBlock extends BaseEntityBlock {

    public static final MapCodec<ItomakiBlock> CODEC = simpleCodec(ItomakiBlock::new);

    public static final DirectionProperty FACING   = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty   SPOOLING = BooleanProperty.create("spooling");

    @Override public MapCodec<ItomakiBlock> codec() { return CODEC; }

    public ItomakiBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(SPOOLING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SPOOLING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(SPOOLING, false);
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    /** Right-click with a Tama → load it. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
            BlockHitResult hit) {
        if (!(stack.getItem() instanceof TamaItem)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        if (level.getBlockEntity(pos) instanceof ItomakiBlockEntity be) {
            if (be.hasTama()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            be.insertTama(stack);
            level.setBlock(pos, state.setValue(SPOOLING, true), 3);
        }
        return ItemInteractionResult.SUCCESS;
    }

    /** Right-click with empty hand → retrieve the Tama. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (level.getBlockEntity(pos) instanceof ItomakiBlockEntity be && be.hasTama()) {
            ItemStack tama = be.retrieveTama();
            level.setBlock(pos, state.setValue(SPOOLING, false), 3);
            if (!player.getInventory().add(tama)) {
                player.drop(tama, false);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** Drop the Tama when the block is broken. */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
            BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ItomakiBlockEntity be && be.hasTama()) {
                ItemStack tama = be.retrieveTama();
                double cx = pos.getX() + 0.5, cy = pos.getY() + 0.75, cz = pos.getZ() + 0.5;
                level.addFreshEntity(new ItemEntity(level, cx, cy, cz, tama));
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // ── Block entity ─────────────────────────────────────────────────────────

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItomakiBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
            : createTickerHelper(type, MusubiBlockEntities.ITOMAKI.get(),
                ItomakiBlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
