package com.sanhiruzu.musubi.block;

import com.sanhiruzu.musubi.blockentity.SpoolRackBlockEntity;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.item.SkeinItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Stores up to four item types (SLOTS × SLOT_CAPACITY items each).
 *
 * Right-click with a skein   → deposit skein contents; skein stays in hand with remainder
 *                              if the rack is full for some variants.
 * Right-click with empty hand → extract one stack (64) from the first non-empty slot.
 * Shift + right-click         → extract all items from all slots.
 */
public class SpoolRackBlock extends BaseEntityBlock {

    public SpoolRackBlock(Properties properties) {
        super(properties);
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
    protected InteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand,
            BlockHitResult hit) {

        if (!(stack.getItem() instanceof SkeinItem)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        SpoolRackBlockEntity be = getBlockEntity(level, pos);
        if (be == null) return InteractionResult.FAIL;

        SkeinContents contents = stack.get(MusubiDataComponents.SKEIN_CONTENTS);
        if (contents == null || contents.count() == 0) return InteractionResult.FAIL;

        SkeinContents remainder = be.depositSkein(contents);
        if (remainder == null) {
            stack.shrink(1); // fully consumed
        } else {
            stack.set(MusubiDataComponents.SKEIN_CONTENTS, remainder);
        }
        level.sendBlockUpdated(pos, state, state, 3);
        return InteractionResult.SUCCESS;
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

    @Nullable
    private static SpoolRackBlockEntity getBlockEntity(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof SpoolRackBlockEntity rack ? rack : null;
    }
}
