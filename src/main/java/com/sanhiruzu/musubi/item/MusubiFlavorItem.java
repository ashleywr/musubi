package com.sanhiruzu.musubi.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Generic material items that only need a compact, styled lore tooltip. */
public class MusubiFlavorItem extends Item {

    public enum Flavor {
        SILK_THREAD,
        SILKWORM_COCOON,
        WOVEN_CLOTH,
        MAGICAL_CLOTH
    }

    private final Flavor flavor;

    public MusubiFlavorItem(Flavor flavor, Properties properties) {
        super(properties);
        this.flavor = flavor;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean showDetails = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        switch (flavor) {
            case SILK_THREAD -> {
                MusubiTooltip.addLine(tooltip, ChatFormatting.GRAY, "musubi.item.silk_thread.summary");
                if (showDetails) {
                    tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                        ChatFormatting.GOLD, Component.translatable("musubi.item.silk_thread.use"),
                        ChatFormatting.GRAY));
                } else {
                    MusubiTooltip.addShiftPrompt(tooltip);
                }
            }
            case SILKWORM_COCOON -> {
                MusubiTooltip.addLine(tooltip, ChatFormatting.AQUA, "musubi.item.silkworm_cocoon.summary");
                if (showDetails) {
                    tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.source"),
                        ChatFormatting.GOLD, Component.translatable("musubi.item.silkworm_cocoon.source"),
                        ChatFormatting.GRAY));
                    tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                        ChatFormatting.GOLD, Component.translatable("musubi.item.silkworm_cocoon.use"),
                        ChatFormatting.GRAY));
                } else {
                    MusubiTooltip.addShiftPrompt(tooltip);
                }
            }
            case WOVEN_CLOTH -> {
                MusubiTooltip.addLine(tooltip, ChatFormatting.WHITE, "musubi.item.woven_cloth.summary");
                if (showDetails) {
                    tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.source"),
                        ChatFormatting.GOLD, Component.translatable("musubi.item.woven_cloth.source"),
                        ChatFormatting.GRAY));
                    tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.role"),
                        ChatFormatting.GOLD, Component.translatable("musubi.item.woven_cloth.role"),
                        ChatFormatting.GRAY));
                } else {
                    MusubiTooltip.addShiftPrompt(tooltip);
                }
            }
            case MAGICAL_CLOTH -> {
                MusubiTooltip.addLine(tooltip, ChatFormatting.LIGHT_PURPLE, "musubi.item.magical_cloth.summary");
                if (showDetails) {
                    tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.source"),
                        ChatFormatting.GOLD, Component.translatable("musubi.item.magical_cloth.source"),
                        ChatFormatting.GRAY));
                    tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.role"),
                        ChatFormatting.GOLD, Component.translatable("musubi.item.magical_cloth.role"),
                        ChatFormatting.GRAY));
                } else {
                    MusubiTooltip.addShiftPrompt(tooltip);
                }
            }
        }
    }
}
