package com.sanhiruzu.musubi.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Shared tooltip formatting helpers for Musubi items and block items. */
public final class MusubiTooltip {

    private MusubiTooltip() {}

    public static void addLine(List<Component> tooltip, ChatFormatting color, String key, Object... args) {
        tooltip.add(Component.translatable(key, args).withStyle(color));
    }

    public static void addLine(List<Component> tooltip, ChatFormatting color, Component text) {
        tooltip.add(text.copy().withStyle(color));
    }

    public static void addHint(List<Component> tooltip, String key, Object... args) {
        tooltip.add(Component.translatable(key, args).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    public static void addShiftPrompt(List<Component> tooltip) {
        tooltip.add(keyHint("Shift", ChatFormatting.YELLOW,
            Component.translatable("musubi.tooltip.more_details"),
            ChatFormatting.GRAY));
    }

    public static void addJapaneseName(List<Component> tooltip, String loreKey) {
        tooltip.add(labelValue(Component.translatable("musubi.tooltip.japanese_name"),
            ChatFormatting.GOLD, Component.translatable(loreKey), ChatFormatting.AQUA));
    }

    public static Component keyBadge(String label, ChatFormatting keyColor) {
        return Component.literal("[")
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal(label).withStyle(keyColor))
            .append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static Component keyHint(String label, ChatFormatting keyColor, Component detail, ChatFormatting detailColor) {
        return Component.literal("")
            .append(keyBadge(label, keyColor))
            .append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY))
            .append(detail.copy().withStyle(detailColor));
    }

    public static void addBullet(List<Component> tooltip, ChatFormatting bulletColor, ChatFormatting textColor,
            Component text) {
        tooltip.add(Component.literal(" - ").withStyle(bulletColor)
            .append(text.copy().withStyle(textColor)));
    }

    public static Component labelValue(Component label, ChatFormatting labelColor, Component value,
            ChatFormatting valueColor) {
        return Component.literal("")
            .append(label.copy().withStyle(labelColor))
            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
            .append(value.copy().withStyle(valueColor));
    }
}
