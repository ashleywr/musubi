package com.sanhiruzu.musubi.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class FiberBundleItem extends Item {

    public FiberBundleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean showDetails = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        MusubiTooltip.addLine(tooltip, ChatFormatting.GOLD, "musubi.item.fiber_bundle.summary");
        if (showDetails) {
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.role"),
                ChatFormatting.GOLD, Component.translatable("musubi.item.fiber_bundle.role"),
                ChatFormatting.GRAY));
        } else {
            MusubiTooltip.addShiftPrompt(tooltip);
        }
    }

    // TODO: use-on-needle interaction to restore charges, consume one bundle per use
}
