package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.component.TamaFilter;
import com.sanhiruzu.musubi.component.TamaStorage;
import com.sanhiruzu.musubi.sound.MusubiSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.List;

/**
 * The tama — a weighted kumihimo bobbin that acts as a one-slot magic bag.
 *
 * <p>It carries two components:
 * <ul>
 *   <li>{@link TamaFilter} — the learned set of item types it binds on pickup.</li>
 *   <li>{@link TamaStorage} — the bound essence (a flat itemId→count ledger).</li>
 * </ul>
 *
 * <p>Interactions (held in main hand):
 * <ul>
 *   <li><b>Sneak + right-click</b> with a sample item in the off-hand — toggle that type on the
 *       filter ("teach by example").</li>
 *   <li><b>Right-click</b> (empty off-hand, not sneaking) — sweep: pull all filtered items already
 *       in the inventory into the tama.</li>
 *   <li>Filtered items <b>bind automatically the moment they are picked up</b> (handled by
 *       {@code InventoryAddMixin}) — they never take an inventory slot.</li>
 *   <li>Right-click a <b>Spool Rack</b> to dump the bound essence onto the wall (handled by
 *       {@code SpoolRackBlock}).</li>
 * </ul>
 *
 * <p>tier governs capacity, not grouping: higher tiers hold more types and more total items.
 */
public class TamaItem extends Item {

    public final int tier;

    public TamaItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    // ── Per-tier limits ─────────────────────────────────────────────────────────

    /** Maximum number of distinct item types this tama's filter may hold. */
    public int filterCapacity() {
        return switch (tier) {
            case 0 -> 4;     // bone
            case 1 -> 8;     // iron
            case 2 -> 16;    // silver
            default -> 64;   // orihime (effectively unlimited)
        };
    }

    /** Radius (blocks) within which a worn tama pulls matching dropped items toward the wearer. */
    public double pickupRange() {
        return switch (tier) {
            case 0 -> 3.0;   // bone
            case 1 -> 4.0;   // iron
            case 2 -> 5.0;   // silver
            default -> 7.0;  // orihime
        };
    }

    /** Maximum total number of items this tama may store. */
    public int storageCapacity() {
        return switch (tier) {
            case 0 -> 4_096;        // bone
            case 1 -> 16_384;       // iron
            case 2 -> 65_536;       // silver
            default -> 2_000_000;   // orihime (effectively unlimited)
        };
    }

    // ── Static binding API (used by the mixin, rack, and sweep) ─────────────────

    /** True if this tama's filter accepts {@code id}. */
    public static boolean accepts(ItemStack tamaStack, ResourceLocation id) {
        if (id == null || !(tamaStack.getItem() instanceof TamaItem)) return false;
        return tamaStack.getOrDefault(MusubiDataComponents.TAMA_FILTER, TamaFilter.EMPTY).contains(id);
    }

    /**
     * Binds up to {@code amount} of {@code id} into the tama, respecting its capacity.
     * Returns the number actually deposited (0 if full or not a tama).
     */
    public static int depositInto(ItemStack tamaStack, ResourceLocation id, int amount) {
        if (id == null || !(tamaStack.getItem() instanceof TamaItem tama)) return 0;
        TamaStorage storage = tamaStack.getOrDefault(MusubiDataComponents.TAMA_STORAGE, TamaStorage.EMPTY);
        int space = tama.storageCapacity() - storage.total();
        if (space <= 0) return 0;
        int deposited = Math.min(space, amount);
        if (deposited <= 0) return 0;
        tamaStack.set(MusubiDataComponents.TAMA_STORAGE, storage.withAdded(id, deposited));
        return deposited;
    }

    // ── Interaction ─────────────────────────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack tama = player.getItemInHand(hand);
        if (!(tama.getItem() instanceof TamaItem)) {
            return InteractionResultHolder.pass(tama);
        }

        if (player.isShiftKeyDown()) {
            return teach(level, player, tama);
        }
        return sweep(level, player, tama);
    }

    /** Sneak path: toggle the off-hand item's type on the filter. */
    private InteractionResultHolder<ItemStack> teach(Level level, Player player, ItemStack tama) {
        ItemStack sample = player.getOffhandItem();
        if (sample.isEmpty()) {
            // Nothing to teach — report the current filter size instead.
            if (!level.isClientSide) {
                TamaFilter f = tama.getOrDefault(MusubiDataComponents.TAMA_FILTER, TamaFilter.EMPTY);
                player.displayClientMessage(
                    Component.translatable("musubi.tama.binds_count", f.size(), filterCapacity()), true);
            }
            return InteractionResultHolder.success(tama);
        }
        if (!level.isClientSide) attune(player, tama, sample);
        return InteractionResultHolder.success(tama);
    }

    /**
     * Server-side: toggle {@code sample}'s item type on the tama's filter, with feedback.
     * Shared by the off-hand sneak-use path and the attune keybind.
     */
    public static void attune(Player player, ItemStack tama, ItemStack sample) {
        if (!(tama.getItem() instanceof TamaItem t) || player.level().isClientSide) return;
        if (sample.isEmpty() || sample.getItem() instanceof TamaItem) {
            player.displayClientMessage(Component.translatable("musubi.tama.attune_none"), true);
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(sample.getItem());
        if (id == null) return;

        Level level = player.level();
        TamaFilter filter = tama.getOrDefault(MusubiDataComponents.TAMA_FILTER, TamaFilter.EMPTY);
        Component name = sample.getHoverName();
        if (filter.contains(id)) {
            tama.set(MusubiDataComponents.TAMA_FILTER, filter.without(id));
            player.displayClientMessage(Component.translatable("musubi.tama.forgot", name), true);
            level.playSound(null, player.blockPosition(), MusubiSounds.NEEDLE_COLLECT.get(),
                SoundSource.PLAYERS, 0.4f, 0.7f);
        } else if (filter.size() >= t.filterCapacity()) {
            player.displayClientMessage(
                Component.translatable("musubi.tama.filter_full", t.filterCapacity()), true);
        } else {
            tama.set(MusubiDataComponents.TAMA_FILTER, filter.with(id));
            player.displayClientMessage(Component.translatable("musubi.tama.taught", name), true);
            level.playSound(null, player.blockPosition(), MusubiSounds.NEEDLE_COLLECT.get(),
                SoundSource.PLAYERS, 0.4f, 1.2f);
        }
    }

    /**
     * Keybind entry point: attune the held sample to the player's active tama (worn belt first,
     * then a held tama, then the first in the inventory).
     */
    public static void attuneFromKey(Player player) {
        if (player.level().isClientSide) return;
        ItemStack tama = findActiveTama(player);
        if (tama == null) return; // nothing to do; the key also just "peeks" at the HUD client-side
        ItemStack sample = sampleFor(player, tama);
        // Peek-only press (no valid sample) — stay silent so checking fullness isn't naggy.
        if (sample.isEmpty() || sample.getItem() instanceof TamaItem) return;
        attune(player, tama, sample);
    }

    /** The player's active tama: worn belt first, then a held tama, then the first in the inventory. */
    public static ItemStack findActiveTama(Player player) {
        ItemStack worn = com.sanhiruzu.musubi.curio.MusubiCurios.findWornTama(player);
        if (worn != null) return worn;
        if (player.getMainHandItem().getItem() instanceof TamaItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof TamaItem) return player.getOffhandItem();
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof TamaItem) return s;
        }
        return null;
    }

    /** The sample to attune: the main-hand item if it isn't the tama, otherwise the off-hand. */
    private static ItemStack sampleFor(Player player, ItemStack tama) {
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && main != tama && !(main.getItem() instanceof TamaItem)) return main;
        return player.getOffhandItem();
    }

    /** Non-sneak path: pull all filtered items already in the inventory into the tama. */
    private InteractionResultHolder<ItemStack> sweep(Level level, Player player, ItemStack tama) {
        if (level.isClientSide) return InteractionResultHolder.success(tama);

        int moved = 0;
        for (ItemStack s : player.getInventory().items) {
            if (s.isEmpty() || s == tama) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
            if (!accepts(tama, id)) continue;
            int dep = depositInto(tama, id, s.getCount());
            if (dep > 0) {
                s.shrink(dep);
                moved += dep;
            }
        }

        if (moved > 0) {
            player.displayClientMessage(Component.translatable("musubi.tama.swept", moved), true);
            TamaStorage st = tama.getOrDefault(MusubiDataComponents.TAMA_STORAGE, TamaStorage.EMPTY);
            spawnBurstParticles((ServerLevel) level, player, displayColor(st));
            level.playSound(null, player.blockPosition(), MusubiSounds.TAMA_BIND.get(),
                SoundSource.PLAYERS, 0.7f, 1.0f);
        } else {
            player.displayClientMessage(Component.translatable("musubi.tama.empty_sweep"), true);
        }
        return InteractionResultHolder.success(tama);
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        TamaFilter filter = stack.getOrDefault(MusubiDataComponents.TAMA_FILTER, TamaFilter.EMPTY);
        TamaStorage storage = stack.getOrDefault(MusubiDataComponents.TAMA_STORAGE, TamaStorage.EMPTY);

        lines.add(Component.translatable("musubi.tama.tier." + tier).withStyle(ChatFormatting.GRAY));

        // What it's holding — the prominent line, with the per-type breakdown beneath.
        if (storage.isEmpty()) {
            lines.add(Component.translatable("musubi.tama.empty").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.translatable("musubi.tama.stored", storage.total(), storageCapacity())
                .withStyle(ChatFormatting.AQUA));
            int shown = 0;
            for (SkeinContents.VariantEntry e : storage.entries()) {
                if (shown >= 4) {
                    lines.add(Component.translatable("musubi.tama.more", storage.distinctTypes() - shown)
                        .withStyle(ChatFormatting.DARK_GRAY));
                    break;
                }
                lines.add(Component.translatable("musubi.tama.entry", itemName(e.itemId()), e.count())
                    .withStyle(ChatFormatting.GRAY));
                shown++;
            }
        }

        // What it's set to bind — shown as type names, clearly labelled (not a bare count).
        if (filter.size() == 0) {
            lines.add(Component.translatable("musubi.tama.binds_empty").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            Component names = joinNames(filter.items());
            lines.add(Component.translatable("musubi.tama.binds", filter.size(), filterCapacity(), names)
                .withStyle(ChatFormatting.DARK_GRAY));
        }

        // Controls — collapsed behind Shift to keep the tooltip tidy.
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            for (String key : new String[]{
                "musubi.tama.help_attune", "musubi.tama.help_key", "musubi.tama.help_sweep",
                "musubi.tama.help_worn", "musubi.tama.help_unload"}) {
                lines.add(Component.translatable(key)
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        } else {
            lines.add(Component.translatable("musubi.tama.help_more")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /** Display name for an item id, falling back to the raw id. */
    private static Component itemName(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id)
            .map(it -> (Component) Component.translatable(it.getDescriptionId()))
            .orElse(Component.literal(id.toString()));
    }

    /** Comma-joined list of item names (first 6, then "…"). */
    private static Component joinNames(List<ResourceLocation> ids) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        int max = Math.min(ids.size(), 6);
        for (int i = 0; i < max; i++) {
            if (i > 0) out.append(", ");
            out.append(itemName(ids.get(i)));
        }
        if (ids.size() > max) out.append(", …");
        return out;
    }

    // ── Bind effect (pickup) ────────────────────────────────────────────────────

    /** Per-player throttle so vein-mining a 64-stack doesn't strobe/spam the effect. */
    private static final java.util.Map<java.util.UUID, Long> LAST_EFFECT = new java.util.HashMap<>();
    private static final long EFFECT_COOLDOWN = 3L;

    /**
     * Magical "the item is pulled up and spun into silk" flourish, played on bind-on-pickup.
     * Throttled per player; safe to call on every pickup. No-op on the client.
     */
    public static void playBindEffect(Player player, ResourceLocation boundId) {
        if (boundId == null || !(player.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Long last = LAST_EFFECT.get(player.getUUID());
        if (last != null && now - last < EFFECT_COOLDOWN) return;
        LAST_EFFECT.put(player.getUUID(), now);

        spawnRisingThread(level, player, colorFromId(boundId.toString()));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            MusubiSounds.TAMA_BIND.get(), SoundSource.PLAYERS,
            0.35f, 1.25f + level.random.nextFloat() * 0.35f);
    }

    /** A short colored helix rising from the feet, capped with a sparkle where it becomes silk. */
    private static void spawnRisingThread(ServerLevel level, Player player, int color) {
        Vector3f rgb = unpackRgb(color);
        double cx = player.getX(), cz = player.getZ();
        double baseY = player.getY() + 0.1;
        double phase = level.getGameTime() * 0.35;

        int points = 7;
        for (int i = 0; i < points; i++) {
            double t = i / (double) points;
            double angle = t * Math.PI * 4.0 + phase;
            double radius = 0.32 * (1.0 - t) + 0.04;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            double y = baseY + t * 1.15;
            // size 0 delta → a precise mote tracing the thread.
            level.sendParticles(new DustParticleOptions(rgb, 0.9f), x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // The thread of light drawing upward.
        level.sendParticles(ParticleTypes.END_ROD, cx, baseY + 0.25, cz, 1, 0.02, 0.35, 0.02, 0.015);
        // Sparkle at the top where the strand winds onto the tama.
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, cx, baseY + 1.05, cz, 2, 0.12, 0.08, 0.12, 0.04);
    }

    // ── Particle effects (sweep) ────────────────────────────────────────────────

    private static int displayColor(TamaStorage storage) {
        if (storage.isEmpty()) return 0xE8D8B0;
        return colorFromId(storage.entries().get(0).itemId().toString());
    }

    /** 0xRRGGBB tint for ambient/UI effects: the dominant stored type, or soft silk cream if empty. */
    public static int contentColor(ItemStack tamaStack) {
        return displayColor(tamaStack.getOrDefault(MusubiDataComponents.TAMA_STORAGE, TamaStorage.EMPTY));
    }

    private static void spawnBurstParticles(ServerLevel level, Player player, int color) {
        Vector3f rgb = unpackRgb(color);
        double px = player.getX(), py = player.getY() + 1.1, pz = player.getZ();
        level.sendParticles(new DustParticleOptions(rgb, 2.0f), px, py, pz, 10, 0.4, 0.3, 0.4, 0.05);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, px, py, pz, 8, 0.35, 0.3, 0.35, 0.1);
        level.sendParticles(ParticleTypes.END_ROD, px, py, pz, 2, 0.1, 0.05, 0.1, 0.15);
    }

    private static Vector3f unpackRgb(int packed) {
        return new Vector3f(
            ((packed >> 16) & 0xFF) / 255f,
            ((packed >>  8) & 0xFF) / 255f,
            ( packed        & 0xFF) / 255f
        );
    }

    private static int colorFromId(String id) {
        int h = id.hashCode();
        int r = Math.max(0x40, (h >>> 16) & 0xFF);
        int g = Math.max(0x40, (h >>> 8)  & 0xFF);
        int b = Math.max(0x40,  h          & 0xFF);
        return (r << 16) | (g << 8) | b;
    }
}
