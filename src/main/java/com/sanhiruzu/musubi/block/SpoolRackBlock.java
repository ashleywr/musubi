package com.sanhiruzu.musubi.block;

import com.mojang.serialization.MapCodec;
import com.sanhiruzu.musubi.blockentity.SpoolRackBlockEntity;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.item.SkeinItem;
import com.sanhiruzu.musubi.sound.MusubiSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
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

        // Dump a Tama's bound essence onto the rack.
        if (stack.getItem() instanceof com.sanhiruzu.musubi.item.TamaItem) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            SpoolRackBlockEntity rack = getBlockEntity(level, pos);
            if (rack == null) return ItemInteractionResult.FAIL;
            return dumpTama(stack, state, level, pos, rack);
        }

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
        level.playSound(null, pos, MusubiSounds.RACK_DEPOSIT.get(), SoundSource.BLOCKS,
            0.8f, 0.9f + level.random.nextFloat() * 0.2f);
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

    // ── Comparator output ─────────────────────────────────────────────────────

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
        SpoolRackBlockEntity be = getBlockEntity(level, pos);
        if (be == null) return 0;
        int total = 0;
        for (int i = 0; i < SpoolRackBlockEntity.SLOTS; i++) total += be.getSlotCount(i);
        if (total == 0) return 0;
        int maxTotal = SpoolRackBlockEntity.SLOTS * SpoolRackBlockEntity.SLOT_CAPACITY;
        return 1 + (int) (total / (float) maxTotal * 14);
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

    /**
     * Dumps a Tama's bound essence into the rack, depositing each type and writing back
     * any remainder that did not fit. The Tama itself stays in hand.
     */
    private ItemInteractionResult dumpTama(
            ItemStack tama, BlockState state, Level level, BlockPos pos, SpoolRackBlockEntity rack) {
        com.sanhiruzu.musubi.component.TamaStorage storage =
            tama.getOrDefault(MusubiDataComponents.TAMA_STORAGE,
                com.sanhiruzu.musubi.component.TamaStorage.EMPTY);
        if (storage.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        java.util.List<SkeinContents.VariantEntry> leftover = new java.util.ArrayList<>();
        int dumped = 0;
        for (SkeinContents.VariantEntry e : storage.entries()) {
            int dep = rack.tryDeposit(e.itemId(), e.count());
            dumped += dep;
            if (dep < e.count()) {
                leftover.add(new SkeinContents.VariantEntry(e.itemId(), e.count() - dep));
            }
        }

        if (dumped == 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        tama.set(MusubiDataComponents.TAMA_STORAGE,
            new com.sanhiruzu.musubi.component.TamaStorage(java.util.List.copyOf(leftover)));
        level.sendBlockUpdated(pos, state, state, 3);
        level.playSound(null, pos, MusubiSounds.RACK_DEPOSIT.get(), SoundSource.BLOCKS,
            0.8f, 0.9f + level.random.nextFloat() * 0.2f);
        return ItemInteractionResult.CONSUME;
    }

    @Nullable
    private static SpoolRackBlockEntity getBlockEntity(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof SpoolRackBlockEntity rack ? rack : null;
    }
}
