package com.sanhiruzu.musubi.block;

import com.sanhiruzu.musubi.blockentity.MarudaiBlockEntity;
import com.sanhiruzu.musubi.blockentity.MusubiBlockEntities;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.item.SkeinItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
 * The marudai — a bobbin-winding stand that routes skein contents to nearby spool racks.
 *
 * Right-click with a skein → mounts it and begins winding.
 *   If no spool racks are within reach, the player gets an actionbar message and the
 *   skein stays in hand.
 *   If already winding, the new skein is rejected with a message.
 *
 * The WINDING block state property is true while a skein is being wound. Models and
 * the future BER can use it to animate the bobbin.
 *
 * FACING is set toward the player on placement.
 */
public class MarudaiBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING  = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty   WINDING = BooleanProperty.create("winding");

    public MarudaiBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(WINDING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WINDING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(WINDING, false);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarudaiBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
            : createTickerHelper(type, MusubiBlockEntities.MARUDAI.get(), MarudaiBlockEntity::tick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    protected InteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {

        if (!(stack.getItem() instanceof SkeinItem)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        MarudaiBlockEntity be = getBlockEntity(level, pos);
        if (be == null) return InteractionResult.FAIL;

        if (be.isWinding()) {
            player.displayClientMessage(
                Component.translatable("musubi.marudai.already_winding"), true);
            return InteractionResult.FAIL;
        }

        SkeinContents contents = stack.get(MusubiDataComponents.SKEIN_CONTENTS);
        if (contents == null || contents.count() == 0) return InteractionResult.FAIL;

        boolean mounted = be.mountSkein(contents);
        if (!mounted) {
            player.displayClientMessage(
                Component.translatable("musubi.marudai.no_racks"), true);
            return InteractionResult.FAIL;
        }

        stack.shrink(1);
        level.setBlock(pos, state.setValue(WINDING, true), 3);
        return InteractionResult.SUCCESS;
    }

    // ── Break behaviour ───────────────────────────────────────────────────────

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            MarudaiBlockEntity be = getBlockEntity(level, pos);
            if (be != null) {
                ItemStack partial = be.takeMountedSkein();
                if (partial != null) {
                    level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                        level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, partial));
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Nullable
    private static MarudaiBlockEntity getBlockEntity(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof MarudaiBlockEntity marudai ? marudai : null;
    }
}
