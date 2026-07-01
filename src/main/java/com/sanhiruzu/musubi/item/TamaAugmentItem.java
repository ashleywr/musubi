package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.Musubi;
import com.sanhiruzu.musubi.sound.MusubiSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A Marudai-woven thread that can be installed into a Tama as an augment.
 */
public class TamaAugmentItem extends Item {

    public enum Kind {
        EMBER_THREAD("tama_ember_thread", ChatFormatting.GOLD),
        REACHING_THREAD("tama_reaching_thread", ChatFormatting.AQUA),
        HOLLOW_THREAD("tama_hollow_thread", ChatFormatting.DARK_GRAY),
        SPIRIT_THREAD("tama_spirit_thread", ChatFormatting.LIGHT_PURPLE);

        private final String registryName;
        private final ChatFormatting accent;

        Kind(String registryName, ChatFormatting accent) {
            this.registryName = registryName;
            this.accent = accent;
        }

        public String registryName() {
            return registryName;
        }

        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, registryName);
        }

        public ChatFormatting accent() {
            return accent;
        }

        public String key(String suffix) {
            return "musubi.item." + registryName + "." + suffix;
        }
    }

    private final Kind kind;

    public TamaAugmentItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean showDetails = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        MusubiTooltip.addLine(tooltip, kind.accent(), kind.key("summary"));
        if (showDetails) {
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.source"),
                ChatFormatting.GOLD, Component.translatable(kind.key("source")),
                ChatFormatting.GRAY));
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                ChatFormatting.GOLD, Component.translatable(kind.key("use")),
                ChatFormatting.GRAY));
            tooltip.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.effect"),
                ChatFormatting.GOLD, Component.translatable(kind.key("effect")),
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
        ItemStack augment = player.getItemInHand(hand);
        ItemStack otherHand = hand == InteractionHand.MAIN_HAND ? player.getItemInHand(InteractionHand.OFF_HAND)
            : player.getItemInHand(InteractionHand.MAIN_HAND);

        if (!(otherHand.getItem() instanceof TamaItem)) {
            return InteractionResultHolder.pass(augment);
        }
        if (level.isClientSide) return InteractionResultHolder.success(augment);

        if (!TamaItem.addAugment(otherHand, kind)) {
            player.displayClientMessage(Component.translatable("musubi.tama.augment.already",
                Component.translatable(getDescriptionId())), true);
            return InteractionResultHolder.pass(augment);
        }

        augment.shrink(1);
        player.displayClientMessage(Component.translatable("musubi.tama.augment.woven",
            Component.translatable(getDescriptionId())), true);
        level.playSound(null, player.blockPosition(), MusubiSounds.NEEDLE_COLLECT.get(),
            SoundSource.PLAYERS, 0.45f, 0.95f);
        return InteractionResultHolder.success(augment);
    }
}
