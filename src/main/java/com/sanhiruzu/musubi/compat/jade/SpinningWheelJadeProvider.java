package com.sanhiruzu.musubi.compat.jade;

import com.sanhiruzu.musubi.blockentity.SpinningWheelBlockEntity;
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

public class SpinningWheelJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final SpinningWheelJadeProvider INSTANCE = new SpinningWheelJadeProvider();
    public static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath("musubi", "spinning_wheel");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof SpinningWheelBlockEntity be)) return;
        data.putInt("rei",     be.getReiStorage().getREI());
        data.putInt("rei_max", be.getReiStorage().getMaxREI());
    }

    @Override
    public ResourceLocation getUid() { return UID; }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        int rei    = data.getInt("rei");
        int reiMax = data.getInt("rei_max");
        if (rei <= 0) return;
        tooltip.add(Component.literal(
            String.format(Locale.ROOT, "REI: %,d / %,d", rei, reiMax))
            .withStyle(ChatFormatting.AQUA));
    }
}
