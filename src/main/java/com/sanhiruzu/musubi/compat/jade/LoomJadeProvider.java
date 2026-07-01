package com.sanhiruzu.musubi.compat.jade;

import com.sanhiruzu.musubi.blockentity.LoomBlockEntity;
import com.sanhiruzu.musubi.item.WeavingPatternItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

public class LoomJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final LoomJadeProvider INSTANCE = new LoomJadeProvider();
    public static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath("musubi", "loom");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof LoomBlockEntity be)) return;
        data.putInt("rei",      be.getReiStorage().getREI());
        data.putInt("rei_max",  be.getReiStorage().getMaxREI());
        data.putFloat("progress", be.getCraftProgress());
        data.putBoolean("has_output", !be.getOutput().isEmpty());
        net.minecraft.world.item.ItemStack target = WeavingPatternItem.getTarget(be.getPattern());
        data.putString("target", target.isEmpty() ? "" : target.getHoverName().getString());
    }

    @Override
    public ResourceLocation getUid() { return UID; }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        int rei    = data.getInt("rei");
        int reiMax = data.getInt("rei_max");
        float progress = data.getFloat("progress");
        boolean hasOutput = data.getBoolean("has_output");
        String target = data.getString("target");
        boolean hasPattern = !target.isEmpty();

        if (rei > 0 || reiMax > 0) {
            tooltip.add(Component.literal(
                String.format(Locale.ROOT, "REI: %,d / %,d", rei, reiMax))
                .withStyle(ChatFormatting.AQUA));
        }

        if (hasPattern) {
            tooltip.add(Component.literal("Pattern: " + target)
                .withStyle(ChatFormatting.WHITE));
        } else {
            tooltip.add(Component.literal("No pattern installed")
                .withStyle(ChatFormatting.DARK_GRAY));
        }

        if (progress > 0f) {
            int pct = (int)(progress * 100);
            tooltip.add(Component.literal(
                String.format(Locale.ROOT, "Weaving: %d%%", pct))
                .withStyle(ChatFormatting.GREEN));
        } else if (hasPattern && rei < LoomBlockEntity.REI_PER_CRAFT) {
            tooltip.add(Component.literal("Waiting for REI...")
                .withStyle(ChatFormatting.YELLOW));
        } else if (hasPattern) {
            tooltip.add(Component.literal("Idle — check ingredients")
                .withStyle(ChatFormatting.GRAY));
        }

        if (hasOutput) {
            tooltip.add(Component.literal("Output ready")
                .withStyle(ChatFormatting.GOLD));
        }
    }
}
