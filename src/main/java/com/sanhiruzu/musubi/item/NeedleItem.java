package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The needle — held in the main hand to sew items in the off-hand into skeins.
 *
 * tier controls how group_key is computed at sewing time:
 *   0 = bone    → broadest matching tag  (e.g. #minecraft:logs)
 *   1 = iron    → exact item ID          (e.g. minecraft:oak_log)
 *   2 = silver  → cross-tag blend        (TBD — mid-game gate)
 *   3 = orihime → anything               (Eternal Starlight gate)
 *
 * Sewing uses a two-phase hold mechanic:
 *   Phase 1 (ticks 0–29): startup animation, free cancel at any point, no durability spent.
 *   Phase 2 (tick 30+):   drains the off-hand stack into a skein; 1 durability consumed
 *                         at Phase 2 entry. Releasing early leaves a partial skein.
 */
public class NeedleItem extends Item {

    /** Ticks of startup before drain begins (1.5 s at 20 t/s). */
    public static final int STARTUP_TICKS = 30;
    /** Ticks between each drained item (~5 items/second base rate). */
    private static final int DRAIN_INTERVAL = 4;

    public final int tier;

    public NeedleItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    // ── Use mechanic ──────────────────────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // BLOCK gives a two-handed concentration pose appropriate for fine work.
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        // Arbitrarily long; the mechanic is release-driven, not duration-driven.
        return 72000;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        int ticksHeld = getUseDuration(stack, entity) - remainingUseTicks;

        if (ticksHeld == STARTUP_TICKS) {
            // Phase 2 entry: verify off-hand has items, spend 1 durability, lock in skein.
            ItemStack offhand = player.getOffhandItem();
            if (offhand.isEmpty()) {
                player.stopUsingItem();
                return;
            }
            String groupKey = computeGroupKey(offhand);
            ensureSkein(player, groupKey);
            stack.hurtAndBreak(1, (ServerLevel) level,
                player instanceof ServerPlayer sp ? sp : null, item -> {});

        } else if (ticksHeld > STARTUP_TICKS && (ticksHeld - STARTUP_TICKS) % DRAIN_INTERVAL == 0) {
            // Phase 2 drain tick: move one off-hand item into the skein.
            ItemStack offhand = player.getOffhandItem();
            if (offhand.isEmpty()) {
                player.stopUsingItem();
                return;
            }
            String groupKey = computeGroupKey(offhand);
            drainOneItem(player, groupKey, offhand);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        int ticksHeld = getUseDuration(stack, entity) - timeCharged;
        if (ticksHeld < STARTUP_TICKS) {
            // Released during Phase 1 — free cancel; no durability spent, no skein created.
            return;
        }
        // Released during Phase 2 — partial skein already in inventory; nothing to finalize.
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("musubi.needle.tier." + tier));
    }

    // ── Sewing helpers ────────────────────────────────────────────────────────

    /**
     * Derives the group key for the given item based on this needle's tier.
     * Tiers 0/2 use the broadest matching item tag (heuristic: shortest tag path).
     * Tiers 1/3 use the exact item registry ID.
     */
    private String computeGroupKey(ItemStack target) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(target.getItem());
        if (itemId == null) return "minecraft:air";

        if (tier == 1 || tier == 3) {
            return itemId.toString();
        }

        // Tier 0/2: prefer the broadest (shortest path) tag containing this item.
        return BuiltInRegistries.ITEM.getHolder(itemId)
            .map(h -> h.tags()
                .map(TagKey::location)
                .map(ResourceLocation::toString)
                .min(Comparator.comparingInt(String::length))
                .orElse(itemId.toString()))
            .orElse(itemId.toString());
    }

    /**
     * Finds or creates a matching skein (same tier + groupKey) in the player's main inventory.
     * Drops the new skein at the player's position if inventory is full.
     */
    private void ensureSkein(Player player, String groupKey) {
        if (findMatchingSkein(player, groupKey) != null) return;

        if (!consumeFromInventory(player, MusubiItems.SILK_THREAD.get())) {
            player.displayClientMessage(
                Component.translatable("musubi.sewing.no_silk"), true);
            player.stopUsingItem();
            return;
        }

        int color = colorFromGroupKey(groupKey);
        SkeinContents initial = new SkeinContents(tier, groupKey, 0, List.of(), color, 0, Optional.empty());
        ItemStack skein = new ItemStack(MusubiItems.SKEIN.get());
        skein.set(MusubiDataComponents.SKEIN_CONTENTS, initial);

        if (!player.getInventory().add(skein)) {
            player.drop(skein, false);
        }
    }

    private static boolean consumeFromInventory(Player player, net.minecraft.world.item.Item item) {
        for (ItemStack s : player.getInventory().items) {
            if (s.is(item)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    /**
     * Takes one item from offhand and records it as a VariantEntry on the matching skein.
     * Merges into an existing variant if the item type is already present in the list.
     */
    private void drainOneItem(Player player, String groupKey, ItemStack offhand) {
        ItemStack skeinStack = findMatchingSkein(player, groupKey);
        if (skeinStack == null || skeinStack.isEmpty()) return;

        SkeinContents contents = skeinStack.get(MusubiDataComponents.SKEIN_CONTENTS);
        if (contents == null) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(offhand.getItem());
        if (itemId == null) return;

        offhand.shrink(1);

        // Merge into existing variant entry for this item type, or append a new one.
        List<SkeinContents.VariantEntry> variants = new ArrayList<>(contents.variants());
        boolean merged = false;
        for (int i = 0; i < variants.size(); i++) {
            if (variants.get(i).itemId().equals(itemId)) {
                variants.set(i, new SkeinContents.VariantEntry(itemId, variants.get(i).count() + 1));
                merged = true;
                break;
            }
        }
        if (!merged) {
            variants.add(new SkeinContents.VariantEntry(itemId, 1));
        }

        skeinStack.set(MusubiDataComponents.SKEIN_CONTENTS, new SkeinContents(
            contents.needleTier(),
            contents.groupKey(),
            contents.count() + 1,
            List.copyOf(variants),
            contents.displayColor(),
            contents.complexityScore(),
            contents.dataRef()
        ));
    }

    /**
     * Searches the player's 36-slot main inventory for a skein with matching tier and groupKey.
     * Returns the live ItemStack reference — mutations on it update the inventory directly.
     */
    private ItemStack findMatchingSkein(Player player, String groupKey) {
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.items) {
            if (s.getItem() instanceof SkeinItem) {
                SkeinContents c = s.get(MusubiDataComponents.SKEIN_CONTENTS);
                if (c != null && c.needleTier() == tier && c.groupKey().equals(groupKey)) {
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * Deterministic display color derived from groupKey hash.
     * Each channel is floored at 0x40 to avoid near-black results.
     */
    private static int colorFromGroupKey(String groupKey) {
        int h = groupKey.hashCode();
        int r = Math.max(0x40, (h >>> 16) & 0xFF);
        int g = Math.max(0x40, (h >>> 8)  & 0xFF);
        int b = Math.max(0x40,  h          & 0xFF);
        return (r << 16) | (g << 8) | b;
    }
}
