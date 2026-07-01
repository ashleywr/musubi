package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.block.MusubiBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Block item wrapper with a tuned tooltip for the machine and ritual blocks. */
public class MusubiBlockItem extends BlockItem {

    public MusubiBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Block block = getBlock();
        boolean showDetails = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        if (block == MusubiBlocks.SPOOL_RACK.get()) {
            MusubiTooltip.addLine(tooltip, ChatFormatting.AQUA, "musubi.block.spool_rack.summary");
            if (showDetails) {
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.spool_rack.use"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.result"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.spool_rack.result"),
                    ChatFormatting.GRAY));
            } else {
                MusubiTooltip.addShiftPrompt(tooltip);
            }
        } else if (block == MusubiBlocks.MARUDAI.get()) {
            MusubiTooltip.addLine(tooltip, ChatFormatting.GOLD, "musubi.block.marudai.summary");
            if (showDetails) {
                MusubiTooltip.addJapaneseName(tooltip, "musubi.block.marudai.lore");
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.marudai.use"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.result"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.marudai.result"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.effect"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.marudai.effect"),
                    ChatFormatting.GRAY));
            } else {
                MusubiTooltip.addShiftPrompt(tooltip);
            }
        } else if (block == MusubiBlocks.SPINNING_WHEEL.get()) {
            MusubiTooltip.addLine(tooltip, ChatFormatting.AQUA, "musubi.block.spinning_wheel.summary");
            if (showDetails) {
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.spinning_wheel.use"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.result"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.spinning_wheel.result"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.effect"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.spinning_wheel.effect"),
                    ChatFormatting.GRAY));
            } else {
                MusubiTooltip.addShiftPrompt(tooltip);
            }
        } else if (block == MusubiBlocks.SHRINE_STONE.get()) {
            MusubiTooltip.addLine(tooltip, ChatFormatting.GOLD, "musubi.block.shrine_stone.summary");
            if (showDetails) {
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.shrine_stone.use"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.effect"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.shrine_stone.effect"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.result"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.shrine_stone.result"),
                    ChatFormatting.GRAY));
            } else {
                MusubiTooltip.addShiftPrompt(tooltip);
            }
        } else if (block == MusubiBlocks.ITOMAKI.get()) {
            MusubiTooltip.addLine(tooltip, ChatFormatting.AQUA, "musubi.block.itomaki.summary");
            if (showDetails) {
                MusubiTooltip.addJapaneseName(tooltip, "musubi.block.itomaki.lore");
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.itomaki.use"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.result"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.itomaki.result"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.effect"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.itomaki.effect"),
                    ChatFormatting.GRAY));
            } else {
                MusubiTooltip.addShiftPrompt(tooltip);
            }
        } else if (block == MusubiBlocks.DONATION_BOX.get()) {
            MusubiTooltip.addLine(tooltip, ChatFormatting.GOLD, "musubi.block.donation_box.summary");
            if (showDetails) {
                MusubiTooltip.addJapaneseName(tooltip, "musubi.block.donation_box.lore");
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.donation_box.use"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.result"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.donation_box.result"),
                    ChatFormatting.GRAY));
            } else {
                MusubiTooltip.addShiftPrompt(tooltip);
            }
        } else if (block == MusubiBlocks.LOOM.get()) {
            MusubiTooltip.addLine(tooltip, ChatFormatting.LIGHT_PURPLE, "musubi.block.loom.summary");
            if (showDetails) {
                MusubiTooltip.addJapaneseName(tooltip, "musubi.block.loom.lore");
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.loom.use"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.result"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.loom.result"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.effect"),
                    ChatFormatting.GOLD, Component.translatable("musubi.block.loom.effect"),
                    ChatFormatting.GRAY));
            } else {
                MusubiTooltip.addShiftPrompt(tooltip);
            }
        } else {
            super.appendHoverText(stack, context, tooltip, flag);
        }
    }
}
