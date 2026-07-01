package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.component.TamaAugments;
import com.sanhiruzu.musubi.component.TamaFilter;
import com.sanhiruzu.musubi.component.TamaStorage;
import com.sanhiruzu.musubi.curio.MusubiCurios;
import com.sanhiruzu.musubi.sound.MusubiSounds;
import com.sanhiruzu.musubi.network.TamaBindVfxPayload;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The tama — a weighted kumihimo bobbin that acts as a one-slot magic bag.
 *
 * <p>It carries three components:
 * <ul>
 *   <li>{@link TamaFilter} — the learned set of item types it binds on pickup.</li>
 *   <li>{@link TamaStorage} — the bound essence (a flat itemId→count ledger).</li>
 *   <li>{@link TamaAugments} — Marudai-woven augment threads installed into the Tama.</li>
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
 *   <li>Right-click an <b>Itomaki</b> to return matching items from your hand into storage, or a
 *       <b>Spool Rack</b> to dump the bound essence onto the wall (handled by
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
        return filterCapacity(ItemStack.EMPTY);
    }

    /** Radius (blocks) within which a worn tama pulls matching dropped items toward the wearer. */
    public double pickupRange() {
        return pickupRange(ItemStack.EMPTY);
    }

    /** Maximum total number of items this tama may store. */
    public int storageCapacity() {
        return storageCapacity(ItemStack.EMPTY);
    }

    /** Maximum number of distinct item types this tama's filter may hold. */
    public int filterCapacity(ItemStack tamaStack) {
        int base = switch (tier) {
            case 0 -> 4;     // bone
            case 1 -> 8;     // iron
            case 2 -> 16;    // silver
            default -> 64;   // orihime (effectively unlimited)
        };
        return base + (hasAugment(tamaStack, TamaAugmentItem.Kind.SPIRIT_THREAD) ? 4 : 0);
    }

    /** Radius (blocks) within which a worn tama pulls matching dropped items toward the wearer. */
    public double pickupRange(ItemStack tamaStack) {
        double base = switch (tier) {
            case 0 -> 3.0;   // bone
            case 1 -> 4.0;   // iron
            case 2 -> 5.0;   // silver
            default -> 7.0;  // orihime
        };
        if (hasAugment(tamaStack, TamaAugmentItem.Kind.REACHING_THREAD)) base += 2.0;
        if (hasAugment(tamaStack, TamaAugmentItem.Kind.SPIRIT_THREAD)) base += 1.0;
        return base;
    }

    /** Maximum total number of items this tama may store. */
    public int storageCapacity(ItemStack tamaStack) {
        int base = switch (tier) {
            case 0 -> 4_096;        // bone
            case 1 -> 16_384;       // iron
            case 2 -> 65_536;       // silver
            default -> 2_000_000;   // orihime (effectively unlimited)
        };
        int bonus = 0;
        if (hasAugment(tamaStack, TamaAugmentItem.Kind.EMBER_THREAD)) {
            bonus += Math.max(1, base / 4);
        }
        if (hasAugment(tamaStack, TamaAugmentItem.Kind.HOLLOW_THREAD)) {
            bonus += Math.max(1, base / 2);
        }
        return base + bonus;
    }

    // ── Augment API ────────────────────────────────────────────────────────────

    public static boolean hasAugment(ItemStack tamaStack, TamaAugmentItem.Kind kind) {
        return hasAugment(tamaStack, kind.id());
    }

    public static boolean hasAugment(ItemStack tamaStack, ResourceLocation id) {
        if (id == null || !(tamaStack.getItem() instanceof TamaItem)) return false;
        return tamaStack.getOrDefault(MusubiDataComponents.TAMA_AUGMENTS, TamaAugments.EMPTY).contains(id);
    }

    public static boolean addAugment(ItemStack tamaStack, TamaAugmentItem.Kind kind) {
        return addAugment(tamaStack, kind.id());
    }

    public static boolean addAugment(ItemStack tamaStack, ResourceLocation id) {
        if (id == null || !(tamaStack.getItem() instanceof TamaItem)) return false;
        TamaAugments augments = tamaStack.getOrDefault(MusubiDataComponents.TAMA_AUGMENTS, TamaAugments.EMPTY);
        if (augments.contains(id)) return false;
        tamaStack.set(MusubiDataComponents.TAMA_AUGMENTS, augments.with(id));
        return true;
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
        int space = tama.storageCapacity(tamaStack) - storage.total();
        if (space <= 0) return 0;
        int deposited = Math.min(space, amount);
        if (deposited <= 0) return 0;
        tamaStack.set(MusubiDataComponents.TAMA_STORAGE, storage.withAdded(id, deposited));
        return deposited;
    }

    /**
     * Binds as many items as possible from {@code stack} into {@code tamaStack}.
     * Returns the number actually moved and shrinks {@code stack} by that amount.
     */
    public static int depositFromStack(ItemStack tamaStack, ItemStack stack) {
        if (stack.isEmpty() || !(tamaStack.getItem() instanceof TamaItem)) return 0;
        if (stack.getItem() instanceof TamaItem) return 0;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!accepts(tamaStack, id)) return 0;

        int deposited = depositInto(tamaStack, id, stack.getCount());
        if (deposited > 0) stack.shrink(deposited);
        return deposited;
    }

    /**
     * Treats the Tama's stored contents as a skein-shaped source for Marudai recipes.
     *
     * The Tama itself is not consumed; the Marudai reads its stored silk ledger as the
     * winding source so the machine can braid augments directly from the Tama and racks.
     */
    @Nullable
    public static SkeinContents asSkeinContents(ItemStack tamaStack) {
        if (!(tamaStack.getItem() instanceof TamaItem tama)) return null;

        TamaStorage storage = tamaStack.getOrDefault(MusubiDataComponents.TAMA_STORAGE, TamaStorage.EMPTY);
        if (storage.isEmpty()) return null;

        return new SkeinContents(
            tama.tier,
            "tama:" + tama.tier,
            storage.total(),
            List.copyOf(storage.entries()),
            contentColor(tamaStack),
            0,
            java.util.Optional.empty()
        );
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
                    Component.translatable("musubi.tama.binds_count", f.size(), filterCapacity(tama)), true);
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
        } else if (filter.size() >= t.filterCapacity(tama)) {
            player.displayClientMessage(
                Component.translatable("musubi.tama.filter_full", t.filterCapacity(tama)), true);
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
            if (!st.isEmpty()) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    TamaBindVfxPayload.wrap(player, displayItemId(st), 12));
            }
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
        boolean showControls = net.minecraft.client.gui.screens.Screen.hasShiftDown();

        ChatFormatting accent = accentColor();
        MusubiTooltip.addLine(lines, accent, "musubi.tama.tooltip.title");
        lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.tama.tooltip.tier"),
            ChatFormatting.GRAY, Component.translatable("musubi.tama.tier." + tier), accent));

        if (tier == 0) {
            MusubiTooltip.addLine(lines, ChatFormatting.WHITE, "musubi.item.bone_tama.summary");
            if (showControls) {
                lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.source"),
                    ChatFormatting.GOLD, Component.translatable("musubi.item.bone_tama.source"),
                    ChatFormatting.GRAY));
                lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.use"),
                    ChatFormatting.GOLD, Component.translatable("musubi.item.bone_tama.use"),
                    ChatFormatting.GRAY));
            } else {
                MusubiTooltip.addShiftPrompt(lines);
            }
        }

        if (storage.isEmpty()) {
            MusubiTooltip.addLine(lines, ChatFormatting.DARK_GRAY, "musubi.tama.empty");
        } else {
            lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.tama.tooltip.storage"),
                ChatFormatting.GRAY,
                Component.literal(storage.total() + " / " + storageCapacity(stack) + " items"),
                ChatFormatting.AQUA));
            MusubiTooltip.addLine(lines, ChatFormatting.GRAY, "musubi.tama.tooltip.contents");
            int shown = 0;
            for (SkeinContents.VariantEntry e : storage.entries()) {
                if (shown >= 4) {
                    MusubiTooltip.addLine(lines, ChatFormatting.DARK_GRAY, "musubi.tama.more",
                        storage.distinctTypes() - shown);
                    break;
                }
                MusubiTooltip.addBullet(lines, ChatFormatting.DARK_GRAY, ChatFormatting.GRAY,
                    Component.translatable("musubi.tama.entry", itemName(e.itemId()), e.count()));
                shown++;
            }
        }

        if (filter.size() == 0) {
            MusubiTooltip.addLine(lines, ChatFormatting.DARK_GRAY, "musubi.tama.binds_empty");
        } else {
            Component names = joinNames(filter.items());
            lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.tama.tooltip.bindings"),
                ChatFormatting.GRAY,
                Component.literal(filter.size() + " / " + filterCapacity(stack) + " types"),
                ChatFormatting.DARK_AQUA));
            lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.tama.tooltip.binding_names"),
                ChatFormatting.DARK_GRAY, names, ChatFormatting.GRAY));
        }

        TamaAugments tamaAugments = tamaStackAugments(stack);
        if (!tamaAugments.isEmpty()) {
            lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.tama.tooltip.augments"),
                ChatFormatting.GRAY, joinNames(tamaAugments.items()),
                ChatFormatting.LIGHT_PURPLE));
        }

        if (showControls) {
            MusubiTooltip.addLine(lines, ChatFormatting.GOLD, "musubi.tama.help_controls");
            MusubiTooltip.addJapaneseName(lines, "musubi.item.tama.lore");
            lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.tama.help_attune.label"),
                ChatFormatting.GOLD, Component.translatable("musubi.tama.help_attune.value"),
                ChatFormatting.GRAY));
            lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.tama.help_key.label"),
                ChatFormatting.GOLD, Component.translatable("musubi.tama.help_key.value"),
                ChatFormatting.GRAY));
            lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.tama.help_sweep.label"),
                ChatFormatting.GOLD, Component.translatable("musubi.tama.help_sweep.value"),
                ChatFormatting.GRAY));
            lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.tama.help_worn.label"),
                ChatFormatting.GOLD, Component.translatable("musubi.tama.help_worn.value"),
                ChatFormatting.GRAY));
            lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.tama.help_unload.label"),
                ChatFormatting.GOLD, Component.translatable("musubi.tama.help_unload.value"),
                ChatFormatting.GRAY));
        } else {
            MusubiTooltip.addShiftPrompt(lines);
        }
    }

    /** Display name for an item id, falling back to the raw id. */
    private static Component itemName(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id)
            .map(it -> (Component) Component.translatable(it.getDescriptionId()))
            .orElse(Component.literal(id.toString()));
    }

    private static TamaAugments tamaStackAugments(ItemStack stack) {
        return stack.getOrDefault(MusubiDataComponents.TAMA_AUGMENTS, TamaAugments.EMPTY);
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

    private ChatFormatting accentColor() {
        return switch (tier) {
            case 0 -> ChatFormatting.WHITE;
            case 1 -> ChatFormatting.GOLD;
            case 2 -> ChatFormatting.AQUA;
            default -> ChatFormatting.LIGHT_PURPLE;
        };
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

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
            TamaBindVfxPayload.wrap(player, boundId, 12));
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            MusubiSounds.TAMA_BIND.get(), SoundSource.PLAYERS,
            0.35f, 1.25f + level.random.nextFloat() * 0.35f);
    }

    /** Server-side flourish for a dropped item moving into a player's Tama. */
    public static void playCollectionEffect(Player player, ItemEntity source, ResourceLocation itemId,
                                            Vec3 targetAnchor, int stackCount) {
        if (itemId == null || !(player.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Long last = LAST_EFFECT.get(player.getUUID());
        if (last != null && now - last < EFFECT_COOLDOWN) return;
        LAST_EFFECT.put(player.getUUID(), now);

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(source,
            TamaBindVfxPayload.collection(source, player, targetAnchor, itemId, Math.max(1, stackCount), 14, now));
        level.playSound(null, source.getX(), source.getY(), source.getZ(),
            MusubiSounds.TAMA_BIND.get(), SoundSource.PLAYERS,
            0.35f, 1.15f + level.random.nextFloat() * 0.25f);
    }

    // ── Particle effects (sweep) ────────────────────────────────────────────────

    private static int displayColor(TamaStorage storage) {
        if (storage.isEmpty()) return 0xE8D8B0;
        return colorFromId(storage.entries().get(0).itemId().toString());
    }

    /** Representative item id for visuals: the first stored item, or null when empty. */
    private static ResourceLocation displayItemId(TamaStorage storage) {
        if (storage.isEmpty()) return null;
        return storage.entries().get(0).itemId();
    }

    /** 0xRRGGBB tint for ambient/UI effects: the dominant stored type, or soft silk cream if empty. */
    public static int contentColor(ItemStack tamaStack) {
        return displayColor(tamaStack.getOrDefault(MusubiDataComponents.TAMA_STORAGE, TamaStorage.EMPTY));
    }

    private static void spawnBurstParticles(ServerLevel level, Vec3 origin, int color) {
        Vector3f rgb = unpackRgb(color);
        double px = origin.x, py = origin.y + 0.12, pz = origin.z;
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
