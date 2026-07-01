package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.block.MusubiBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class SilkwormItem extends Item {

    public SilkwormItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean showDetails = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        MusubiTooltip.addLine(tooltip, ChatFormatting.GREEN, "musubi.item.silkworm.summary");
        if (showDetails) {
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                ChatFormatting.GOLD, Component.translatable("musubi.item.silkworm.use"),
                ChatFormatting.GRAY));
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.result"),
                ChatFormatting.GOLD, Component.translatable("musubi.item.silkworm.result"),
                ChatFormatting.GRAY));
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.effect"),
                ChatFormatting.GOLD, Component.translatable("musubi.item.silkworm.effect"),
                ChatFormatting.GRAY));
        } else {
            MusubiTooltip.addShiftPrompt(tooltip);
        }
    }

    /**
     * Right-clicking any face of a cherry leaf places the silkworm in the block
     * directly below that leaf, where it will slowly spin a cocoon over time.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);

        if (!state.is(Blocks.CHERRY_LEAVES)) return InteractionResult.PASS;

        BlockPos placePos = pos.below();
        if (!level.getBlockState(placePos).canBeReplaced()) return InteractionResult.FAIL;

        if (!level.isClientSide) {
            level.setBlock(placePos, MusubiBlocks.SILKWORM.get().defaultBlockState(), Block.UPDATE_ALL);
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
