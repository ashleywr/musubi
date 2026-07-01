package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.MusubiTags;
import com.sanhiruzu.musubi.block.MusubiBlocks;
import com.sanhiruzu.musubi.blockentity.ShinbokuBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean showDetails = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        MusubiTooltip.addLine(tooltip, ChatFormatting.GOLD, "musubi.item.shimenawa.summary");
        if (showDetails) {
            MusubiTooltip.addJapaneseName(tooltip, "musubi.item.shimenawa.lore");
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                ChatFormatting.GOLD, Component.translatable("musubi.item.shimenawa.use"),
                ChatFormatting.GRAY));
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.result"),
                ChatFormatting.GOLD, Component.translatable("musubi.item.shimenawa.result"),
                ChatFormatting.GRAY));
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.effect"),
                ChatFormatting.GOLD, Component.translatable("musubi.item.shimenawa.effect"),
                ChatFormatting.GRAY));
        } else {
            MusubiTooltip.addShiftPrompt(tooltip);
        }
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
