package com.sanhiruzu.musubi.util;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory insertion helpers that bypass Inventory.add(ItemStack).
 *
 * Musubi uses an Inventory.add mixin for bind-on-pickup Tama behavior. Block outputs that are
 * already being handed directly to the player should not pass through that hook, or they can be
 * swallowed into a Tama instead of entering the inventory.
 */
public final class PlayerInventoryUtil {

    private PlayerInventoryUtil() {}

    public static void giveDirect(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;

        ItemStack remaining = stack.copy();
        Inventory inventory = player.getInventory();

        for (int i = 0; i < inventory.items.size() && !remaining.isEmpty(); i++) {
            ItemStack existing = inventory.items.get(i);
            if (existing.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(existing, remaining)) continue;

            int space = existing.getMaxStackSize() - existing.getCount();
            if (space <= 0) continue;

            int moved = Math.min(space, remaining.getCount());
            existing.grow(moved);
            remaining.shrink(moved);
        }

        for (int i = 0; i < inventory.items.size() && !remaining.isEmpty(); i++) {
            ItemStack existing = inventory.items.get(i);
            if (!existing.isEmpty()) continue;

            int moved = Math.min(remaining.getMaxStackSize(), remaining.getCount());
            inventory.items.set(i, remaining.split(moved));
        }

        if (!remaining.isEmpty()) {
            player.drop(remaining, false);
        }
    }
}
