package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.component.MusubiDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A weaving pattern (織図, orizu) for the loom autocrafter.
 *
 * A blank pattern carries no data. Crafting a blank pattern together with any
 * single item encodes it with that item as its target (see PatternEncodingRecipe).
 * Insert an encoded pattern into a loom and it will repeatedly craft the target,
 * pulling ingredients from adjacent containers and spending REI per craft.
 */
public class WeavingPatternItem extends Item {

    public WeavingPatternItem(Properties properties) {
        super(properties);
    }

    /** The encoded target item (count 1), or EMPTY if this pattern is blank. */
    public static ItemStack getTarget(ItemStack pattern) {
        ItemStack target = pattern.get(MusubiDataComponents.PATTERN_TARGET.get());
        return target == null ? ItemStack.EMPTY : target;
    }

    public static boolean isEncoded(ItemStack pattern) {
        return !getTarget(pattern).isEmpty();
    }

    /** Build an encoded pattern that targets a copy (count 1) of {@code target}. */
    public static ItemStack encode(ItemStack target) {
        ItemStack pattern = new ItemStack(MusubiItems.WEAVING_PATTERN.get());
        ItemStack one = target.copy();
        one.setCount(1);
        pattern.set(MusubiDataComponents.PATTERN_TARGET.get(), one);
        return pattern;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        ItemStack target = getTarget(stack);
        boolean showDetails = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        if (target.isEmpty()) {
            MusubiTooltip.addLine(tooltip, ChatFormatting.GOLD, "musubi.weaving_pattern.blank.title");
            MusubiTooltip.addLine(tooltip, ChatFormatting.GRAY, "musubi.weaving_pattern.blank.summary");
            if (showDetails) {
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.mode"),
                    ChatFormatting.GOLD, Component.translatable("musubi.weaving_pattern.blank.title"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                    ChatFormatting.GOLD, Component.translatable("musubi.weaving_pattern.use"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.result"),
                    ChatFormatting.GOLD, Component.translatable("musubi.weaving_pattern.result"),
                    ChatFormatting.GRAY));
            } else {
                MusubiTooltip.addShiftPrompt(tooltip);
            }
        } else {
            MusubiTooltip.addLine(tooltip, ChatFormatting.AQUA, "musubi.weaving_pattern.encoded.title");
            MusubiTooltip.addLine(tooltip, ChatFormatting.GRAY, "musubi.weaving_pattern.encoded.summary");
            if (showDetails) {
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.target"),
                    ChatFormatting.GOLD, target.getHoverName(), ChatFormatting.AQUA));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.mode"),
                    ChatFormatting.GOLD, Component.translatable("musubi.weaving_pattern.encoded.title"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                    ChatFormatting.GOLD, Component.translatable("musubi.weaving_pattern.use"),
                    ChatFormatting.GRAY));
                tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.result"),
                    ChatFormatting.GOLD, Component.translatable("musubi.weaving_pattern.result"),
                    ChatFormatting.GRAY));
            } else {
                MusubiTooltip.addShiftPrompt(tooltip);
            }
        }
    }
}
