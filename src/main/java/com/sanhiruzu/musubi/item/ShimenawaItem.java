package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.MusubiTags;
import com.sanhiruzu.musubi.block.MusubiBlocks;
import com.sanhiruzu.musubi.blockentity.ShinbokuBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Shimenawa (注連縄) — a sacred twisted rope strung with paper zigzags (shide).
 *
 * Using it on a valid block (any log, amethyst cluster, smooth stone, etc.) wraps
 * the block in the rope, consecrating it as a Shinboku (神木). The original block
 * is preserved inside the Shinboku and returned when the structure is dismantled.
 *
 * Crafted from wheat straw and paper; single-use but recovered when the Shinboku
 * is mined.
 */
public class ShimenawaItem extends Item {

    public ShimenawaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState target = level.getBlockState(pos);

        // Must be a valid Shinboku candidate.
        if (!target.is(MusubiTags.Blocks.SHINBOKU_VALID)) {
            return InteractionResult.PASS;
        }

        // Don't wrap blocks that are already Shinboku.
        if (target.is(MusubiBlocks.SHINBOKU.get())) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) return InteractionResult.SUCCESS;

        // Place the Shinboku block and record what was there.
        level.setBlock(pos, MusubiBlocks.SHINBOKU.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof ShinbokuBlockEntity be) {
            be.setWrappedBlock(target);
        }

        level.playSound(null, pos, SoundEvents.WOOL_FALL,
            SoundSource.BLOCKS, 0.8f, 0.8f);

        if (!ctx.getPlayer().isCreative()) {
            ctx.getItemInHand().shrink(1);
        }

        return InteractionResult.SUCCESS;
    }
}
