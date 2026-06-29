package com.sanhiruzu.musubi.compat.jade;

import com.sanhiruzu.musubi.blockentity.SpoolRackBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

/**
 * Jade tooltip for the Spool Rack — shows each occupied slot's item name and count.
 * Implements both IServerDataProvider (runs on server, writes slot data to NBT)
 * and IBlockComponentProvider (runs on client, reads that NBT and builds the tooltip).
 */
public class SpoolRackJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    public static final SpoolRackJadeProvider INSTANCE = new SpoolRackJadeProvider();
    public static final ResourceLocation UID =
        ResourceLocation.fromNamespaceAndPath("musubi", "spool_rack");

    // ── Server side ───────────────────────────────────────────────────────────

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof SpoolRackBlockEntity be)) return;
        ListTag slots = new ListTag();
        for (int i = 0; i < SpoolRackBlockEntity.SLOTS; i++) {
            ResourceLocation id = be.getSlotItem(i);
            int count = be.getSlotCount(i);
            if (id == null || count <= 0) continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.toString());
            entry.putInt("count", count);
            slots.add(entry);
        }
        data.put("slots", slots);
    }

    @Override
    public ResourceLocation getUid() { return UID; }

    // ── Client side ───────────────────────────────────────────────────────────

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        ListTag slots = accessor.getServerData().getList("slots", 10);
        if (slots.isEmpty()) {
            tooltip.add(Component.translatable("musubi.jade.spool_rack.empty")
                .withStyle(ChatFormatting.GRAY));
            return;
        }
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag entry = slots.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            if (id == null) continue;
            int count = entry.getInt("count");

            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
            Component name = (item == Items.AIR)
                ? Component.literal(id.toString()).withStyle(ChatFormatting.RED)
                : new ItemStack(item).getHoverName();

            tooltip.add(Component.literal("")
                .append(name)
                .append(Component.literal(
                    String.format(Locale.ROOT, " × %,d", count))
                    .withStyle(ChatFormatting.GRAY)));
        }
    }
}
