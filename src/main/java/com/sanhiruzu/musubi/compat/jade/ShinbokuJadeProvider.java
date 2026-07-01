package com.sanhiruzu.musubi.compat.jade;

import com.sanhiruzu.musubi.blockentity.ShinbokuBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;
import java.util.Optional;

public class ShinbokuJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final ShinbokuJadeProvider INSTANCE = new ShinbokuJadeProvider();
    public static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath("musubi", "shinboku");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ShinbokuBlockEntity be)) return;
        data.putInt("rei",           be.getReiStorage().getREI());
        data.putInt("rei_max",       be.getReiStorage().getMaxREI());
        data.putInt("base_rate",     be.getBaseRate());
        data.putInt("natural_bonus", be.getNaturalBonus());
        ResourceLocation wrappedId = be.getWrappedBlockId();
        if (wrappedId != null) data.putString("wrapped_id", wrappedId.toString());
    }

    @Override
    public ResourceLocation getUid() { return UID; }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        int rei    = data.getInt("rei");
        int reiMax = data.getInt("rei_max");
        int base   = data.getInt("base_rate");
        int bonus  = data.getInt("natural_bonus");

        tooltip.add(Component.literal(
            String.format(Locale.ROOT, "REI: %,d / %,d", rei, reiMax))
            .withStyle(ChatFormatting.AQUA));

        if (data.contains("wrapped_id")) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString("wrapped_id"));
            Component name = Component.literal("?").withStyle(ChatFormatting.RED);
            if (id != null) {
                Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
                if (block.isPresent()) {
                    name = new ItemStack(block.get().asItem()).getHoverName();
                    if (block.get().asItem() == Items.AIR) {
                        name = Component.literal(id.toString()).withStyle(ChatFormatting.GRAY);
                    }
                }
            }
            tooltip.add(Component.literal("Wrapped: ").withStyle(ChatFormatting.GRAY)
                .append(name));
        }

        String rateStr = bonus > 0
            ? String.format(Locale.ROOT, "Rate: %d + %d/t", base, bonus)
            : String.format(Locale.ROOT, "Rate: %d/t", base);
        tooltip.add(Component.literal(rateStr).withStyle(ChatFormatting.GRAY));
    }
}
