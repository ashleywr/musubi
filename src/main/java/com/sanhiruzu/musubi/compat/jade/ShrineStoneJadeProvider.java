package com.sanhiruzu.musubi.compat.jade;

import com.sanhiruzu.musubi.blockentity.ShrineStoneBlockEntity;
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

public class ShrineStoneJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final ShrineStoneJadeProvider INSTANCE = new ShrineStoneJadeProvider();
    public static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath("musubi", "shrine_stone");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ShrineStoneBlockEntity be)) return;
        data.putInt("rei",           be.getREI());
        data.putInt("rei_max",       be.getMaxREI());
        data.putInt("blessed_timer", be.getBlessedTimer());
        data.putInt("rack_bonus",    be.getRackBonus());
        int base = be.isBlessed()
            ? ShrineStoneBlockEntity.BASE_RATE * 2
            : ShrineStoneBlockEntity.BASE_RATE;
        data.putInt("base_rate", base);
    }

    @Override
    public ResourceLocation getUid() { return UID; }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        int rei    = data.getInt("rei");
        int reiMax = data.getInt("rei_max");
        int base   = data.getInt("base_rate");
        int bonus  = data.getInt("rack_bonus");
        int blessed = data.getInt("blessed_timer");

        tooltip.add(Component.literal(
            String.format(Locale.ROOT, "REI: %,d / %,d", rei, reiMax))
            .withStyle(ChatFormatting.AQUA));

        String rateStr = bonus > 0
            ? String.format(Locale.ROOT, "Rate: %d + %d/t", base, bonus)
            : String.format(Locale.ROOT, "Rate: %d/t", base);
        tooltip.add(Component.literal(rateStr).withStyle(ChatFormatting.GRAY));

        if (blessed > 0) {
            int totalSecs = blessed / 20;
            String timeStr = totalSecs >= 60
                ? String.format(Locale.ROOT, "%dm %ds", totalSecs / 60, totalSecs % 60)
                : totalSecs + "s";
            tooltip.add(Component.literal("Blessed: " + timeStr)
                .withStyle(ChatFormatting.YELLOW));
        }
    }
}
