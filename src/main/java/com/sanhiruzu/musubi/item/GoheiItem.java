package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.blockentity.ShrineStoneBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Gohei (御幣) — a ritual wand with folded paper streamers used in Shinto
 * purification rites (harae). Waving it near a Shrine Stone consecrates the
 * area, doubling REI generation for 5 minutes.
 *
 * Has 20 uses before the streamers are spent.
 */
public class GoheiItem extends Item {

    public static final int BLESS_RANGE = 8;
    public static final int BLESS_TICKS = 6000; // 5 minutes

    public GoheiItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean showDetails = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        MusubiTooltip.addLine(tooltip, ChatFormatting.GOLD, "musubi.item.gohei.summary");
        if (showDetails) {
            MusubiTooltip.addJapaneseName(tooltip, "musubi.item.gohei.lore");
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                ChatFormatting.GOLD, Component.translatable("musubi.item.gohei.use"),
                ChatFormatting.GRAY));
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.effect"),
                ChatFormatting.GOLD, Component.translatable("musubi.item.gohei.effect"),
                ChatFormatting.GRAY));
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.durability"),
                ChatFormatting.GOLD, Component.translatable("musubi.item.gohei.durability"),
                ChatFormatting.GRAY));
        } else {
            MusubiTooltip.addShiftPrompt(tooltip);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) return InteractionResultHolder.success(stack);

        BlockPos origin = player.blockPosition();
        int count = 0;

        for (BlockPos p : BlockPos.betweenClosed(
                origin.offset(-BLESS_RANGE, -BLESS_RANGE, -BLESS_RANGE),
                origin.offset( BLESS_RANGE,  BLESS_RANGE,  BLESS_RANGE))) {
            if (level.getBlockEntity(p) instanceof ShrineStoneBlockEntity shrine) {
                shrine.bless(BLESS_TICKS);
                count++;
            }
        }

        if (count > 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.9f, 1.3f);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1.2, player.getZ(),
                    24, 0.5, 0.5, 0.5, 0.05);
            }
            stack.hurtAndBreak(1, (LivingEntity) player, LivingEntity.getSlotForHand(hand));
            player.getCooldowns().addCooldown(this, 40); // 2-second cooldown between waves
        }

        return InteractionResultHolder.success(stack);
    }
}
