package com.sanhiruzu.musubi.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class MagicalSilkThreadItem extends Item {

    public MagicalSilkThreadItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean showDetails = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        MusubiTooltip.addLine(tooltip, ChatFormatting.LIGHT_PURPLE, "musubi.item.magical_silk_thread.summary");
        if (showDetails) {
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                ChatFormatting.GOLD, Component.translatable("musubi.item.magical_silk_thread.use"),
                ChatFormatting.GRAY));
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.effect"),
                ChatFormatting.GOLD, Component.translatable("musubi.item.magical_silk_thread.effect"),
                ChatFormatting.GRAY));
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.cost"),
                ChatFormatting.GOLD, Component.translatable("musubi.item.magical_silk_thread.cost"),
                ChatFormatting.GRAY));
        } else {
            MusubiTooltip.addShiftPrompt(tooltip);
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack thread = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack other = player.getItemInHand(otherHand);

        if (!(other.getItem() instanceof TamaItem)) {
            return InteractionResultHolder.pass(thread);
        }
        if (other.getDamageValue() <= 0) {
            return InteractionResultHolder.pass(thread);
        }
        if (level.isClientSide) return InteractionResultHolder.success(thread);

        int repairAmount = Math.max(1, other.getMaxDamage() / 4);
        other.setDamageValue(Math.max(0, other.getDamageValue() - repairAmount));
        thread.shrink(1);
        level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP,
            SoundSource.PLAYERS, 0.5f, 1.3f);
        player.displayClientMessage(Component.translatable("musubi.magical_silk.charged"), true);
        return InteractionResultHolder.success(thread);
    }
}
