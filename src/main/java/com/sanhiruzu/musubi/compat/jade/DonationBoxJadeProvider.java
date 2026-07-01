package com.sanhiruzu.musubi.compat.jade;

import com.sanhiruzu.musubi.blockentity.DonationBoxBlockEntity;
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

public class DonationBoxJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final DonationBoxJadeProvider INSTANCE = new DonationBoxJadeProvider();
    public static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath("musubi", "donation_box");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof DonationBoxBlockEntity be)) return;
        data.putInt("rei",         be.getReiStorage().getREI());
        data.putInt("rei_max",     be.getReiStorage().getMaxREI());
        data.putInt("pending_rei", be.getPendingREI());
        data.putInt("bow_count",   be.getBowCount());
    }

    @Override
    public ResourceLocation getUid() { return UID; }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        int rei      = data.getInt("rei");
        int reiMax   = data.getInt("rei_max");
        int pending  = data.getInt("pending_rei");
        int bowCount = data.getInt("bow_count");

        tooltip.add(Component.literal(
            String.format(Locale.ROOT, "REI: %,d / %,d", rei, reiMax))
            .withStyle(ChatFormatting.AQUA));

        if (pending > 0) {
            tooltip.add(Component.literal(
                String.format(Locale.ROOT, "Pending: %,d REI", pending))
                .withStyle(ChatFormatting.GOLD));
        }

        if (bowCount > 0) {
            tooltip.add(Component.literal("Awaiting 2nd bow...")
                .withStyle(ChatFormatting.YELLOW));
        }
    }
}
