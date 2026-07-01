package com.sanhiruzu.musubi.blockentity;

import com.sanhiruzu.musubi.block.SpoolRackBlock;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.item.ItemTierRegistry;
import com.sanhiruzu.musubi.item.MusubiItems;
import com.sanhiruzu.musubi.util.PlayerInventoryUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Four independent skein slots, each capped at SLOT_CAPACITY items.
 *
 * Each slot stores a compact {@link SkeinContents} payload rather than raw ItemStacks.
 * That keeps the rack lightweight on disk/network while preserving the full skein data
 * when a player takes it back off the peg.
 *
 * TODO: replace the fallback color hash with a shared palette lookup if the art direction
 * settles on canonical family colors instead of deterministic item-derived colors.
 * TODO: if the rack needs more than four families per block, replace the fixed slot array
 * with a real color-family map and update the renderer accordingly.
 *
 * Extraction:
 *   right-click       -> one full skein from the first non-empty slot
 *   shift-right-click -> all skeins from all slots, given to player or dropped
 */
public class SpoolRackBlockEntity extends BlockEntity {

    public static final int SLOTS = 4;
    public static final int SLOT_CAPACITY = 2048;

    private final SkeinContents[] slots = new SkeinContents[SLOTS];

    public SpoolRackBlockEntity(BlockPos pos, BlockState state) {
        super(MusubiBlockEntities.SPOOL_RACK.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        syncLoadedState();
    }

    // ── Deposit ───────────────────────────────────────────────────────────────

    /**
     * Deposits as much of the skein as the rack can hold.
     * Returns null if the skein was fully consumed, or a new SkeinContents
     * containing only the variants that did not fit.
     */
    @Nullable
    public SkeinContents depositSkein(SkeinContents contents) {
        List<SkeinContents.VariantEntry> remaining = new ArrayList<>();
        for (SkeinContents.VariantEntry entry : contents.variants()) {
            int deposited = depositItem(contents, entry.itemId(), entry.count());
            int leftover = entry.count() - deposited;
            if (leftover > 0) {
                remaining.add(new SkeinContents.VariantEntry(entry.itemId(), leftover));
            }
        }
        setChanged();
        syncLoadedState();
        if (remaining.isEmpty()) return null;
        int remainingCount = remaining.stream().mapToInt(SkeinContents.VariantEntry::count).sum();
        return new SkeinContents(
            contents.needleTier(), contents.groupKey(), remainingCount,
            List.copyOf(remaining), contents.displayColor(),
            contents.complexityScore(), contents.dataRef()
        );
    }

    /**
     * Returns true if this rack has space for at least one more item of the given type.
     * This is used by MarudaiBlockEntity for hopper simulate checks so loaders stop
     * pushing when racks are full.
     */
    public boolean canAccept(ResourceLocation itemId) {
        SkeinContents candidate = makeContents(null, itemId, 1);
        for (int i = 0; i < SLOTS; i++) {
            SkeinContents slot = slots[i];
            if (slot != null && canMerge(slot, candidate) && slot.count() < SLOT_CAPACITY) return true;
            if (slot == null) return true;
        }
        return false;
    }

    /** Returns the slot index that already holds {@code itemId}, or -1. */
    public int getSlotForItem(ResourceLocation itemId) {
        for (int i = 0; i < SLOTS; i++) {
            SkeinContents slot = slots[i];
            if (slot != null && !slot.variants().isEmpty() && itemId.equals(slot.variants().get(0).itemId())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Public entry-point for the Marudai to deposit individual items one at a time.
     * Returns the number actually deposited (0 if no space).
     */
    public int tryDeposit(ResourceLocation itemId, int amount) {
        int deposited = depositItem(null, itemId, amount);
        if (deposited > 0) {
            setChanged();
            syncLoadedState();
        }
        return deposited;
    }

    /**
     * Source-aware deposit for skeins and skein-derived item flow.
     * Returns the number actually deposited (0 if no space).
     */
    public int tryDeposit(@Nullable SkeinContents source, ResourceLocation itemId, int amount) {
        int deposited = depositItem(source, itemId, amount);
        if (deposited > 0) {
            setChanged();
            syncLoadedState();
        }
        return deposited;
    }

    /**
     * Extracts up to {@code amount} items of the given type from this rack.
     * Returns the number actually extracted.
     */
    public int tryExtract(ResourceLocation itemId, int amount) {
        int extracted = extractItem(itemId, amount);
        if (extracted > 0) {
            setChanged();
            syncLoadedState();
        }
        return extracted;
    }

    /**
     * Deposits up to {@code amount} items of the given type.
     * Returns the number actually deposited (may be less than amount if the rack fills).
     */
    private int depositItem(@Nullable SkeinContents source, ResourceLocation itemId, int amount) {
        if (amount <= 0) return 0;

        SkeinContents addition = makeContents(source, itemId, amount);

        // Prefer an existing compatible slot.
        for (int i = 0; i < SLOTS; i++) {
            SkeinContents slot = slots[i];
            if (slot == null) continue;
            if (canMerge(slot, addition)) {
                int space = SLOT_CAPACITY - slot.count();
                int deposited = Math.min(space, amount);
                if (deposited <= 0) return 0;
                slots[i] = slot.mergeWith(makeContents(source, itemId, deposited));
                return deposited;
            }
        }

        // Fall back to the first empty slot.
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i] == null) {
                int deposited = Math.min(SLOT_CAPACITY, amount);
                slots[i] = makeContents(source, itemId, deposited);
                return deposited;
            }
        }
        return 0;
    }

    /**
     * Removes up to {@code amount} items of the given type from the rack.
     * Returns the number actually removed.
     */
    private int extractItem(ResourceLocation itemId, int amount) {
        for (int i = 0; i < SLOTS; i++) {
            SkeinContents slot = slots[i];
            if (slot == null || slot.count() <= 0 || slot.variants().isEmpty()) continue;
            if (slot.variants().stream().noneMatch(v -> itemId.equals(v.itemId()))) continue;

            int remainingToRemove = amount;
            int removed = 0;
            List<SkeinContents.VariantEntry> nextVariants = new ArrayList<>();
            for (SkeinContents.VariantEntry variant : slot.variants()) {
                if (!itemId.equals(variant.itemId()) || remainingToRemove <= 0) {
                    nextVariants.add(variant);
                    continue;
                }

                int take = Math.min(variant.count(), remainingToRemove);
                removed += take;
                remainingToRemove -= take;
                int leftover = variant.count() - take;
                if (leftover > 0) {
                    nextVariants.add(new SkeinContents.VariantEntry(variant.itemId(), leftover));
                }
            }

            if (removed <= 0) continue;

            int newCount = slot.count() - removed;
            if (newCount <= 0) {
                clearSlot(i);
            } else {
                slots[i] = new SkeinContents(
                    slot.needleTier(),
                    slot.groupKey(),
                    newCount,
                    List.copyOf(nextVariants),
                    slot.displayColor(),
                    slot.complexityScore(),
                    slot.dataRef()
                );
            }
            return removed;
        }
        return 0;
    }

    // ── Extraction ────────────────────────────────────────────────────────────

    /**
     * Gives the player one full skein from the first non-empty slot.
     */
    public void extractOne(Player player) {
        for (int i = 0; i < SLOTS; i++) {
            SkeinContents slot = slots[i];
            if (slot == null || slot.count() <= 0) continue;
            give(player, createSkeinStack(slot));
            clearSlot(i);
            setChanged();
            syncLoadedState();
            break;
        }
    }

    /**
     * Gives the player all skeins from all slots.
     */
    public void extractAll(Player player) {
        for (int i = 0; i < SLOTS; i++) {
            SkeinContents slot = slots[i];
            if (slot == null || slot.count() <= 0) continue;
            give(player, createSkeinStack(slot));
            clearSlot(i);
        }
        setChanged();
        syncLoadedState();
    }

    private void give(Player player, ItemStack stack) {
        PlayerInventoryUtil.giveDirect(player, stack);
    }

    private void clearSlot(int i) {
        slots[i] = null;
    }

    /** True if the rack contains any stored items. */
    public boolean hasContents() {
        for (int i = 0; i < SLOTS; i++) {
            SkeinContents slot = slots[i];
            if (slot != null && slot.count() > 0) return true;
        }
        return false;
    }

    /** Mirrors inventory occupancy into the blockstate's loaded property. */
    private void syncLoadedState() {
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        boolean loaded = hasContents();
        if (state.hasProperty(SpoolRackBlock.LOADED) && state.getValue(SpoolRackBlock.LOADED) != loaded) {
            level.setBlock(worldPosition, state.setValue(SpoolRackBlock.LOADED, loaded), 3);
        }
    }

    /**
     * Drops all stored skeins as item entities at the given position.
     * Called by SpoolRackBlock.onRemove so contents are never silently lost.
     */
    public void dropContents(net.minecraft.world.level.Level level, BlockPos pos) {
        for (int i = 0; i < SLOTS; i++) {
            SkeinContents slot = slots[i];
            if (slot == null || slot.count() <= 0) continue;

            net.minecraft.world.entity.item.ItemEntity entity =
                new net.minecraft.world.entity.item.ItemEntity(
                    level,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    createSkeinStack(slot)
                );
            level.addFreshEntity(entity);
            clearSlot(i);
        }
    }

    // ── Read-only accessors (for renderer / tooltip) ─────────────────────────

    public @Nullable ResourceLocation getSlotItem(int slot) {
        SkeinContents contents = slots[slot];
        if (contents == null || contents.variants().isEmpty()) return null;
        return contents.variants().get(0).itemId();
    }

    public int getSlotCount(int slot) {
        SkeinContents contents = slots[slot];
        return contents == null ? 0 : contents.count();
    }

    public @Nullable SkeinContents getSlotContents(int slot) {
        return slots[slot];
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag slotTags = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            SkeinContents contents = slots[i];
            if (contents == null || contents.count() <= 0) continue;

            CompoundTag slot = new CompoundTag();
            slot.putInt("slot", i);
            slot.put("contents", saveContents(contents));
            slotTags.add(slot);
        }
        tag.put("slots", slotTags);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Arrays.fill(slots, null);

        ListTag slotTags = tag.getList("slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < slotTags.size(); i++) {
            CompoundTag slotTag = slotTags.getCompound(i);
            int idx = slotTag.getInt("slot");
            if (idx < 0 || idx >= SLOTS) continue;

            if (slotTag.contains("contents", Tag.TAG_COMPOUND)) {
                slots[idx] = loadContents(slotTag.getCompound("contents"));
                continue;
            }

            // Backward compatibility for the legacy raw-item save format.
            ResourceLocation id = ResourceLocation.tryParse(slotTag.getString("item"));
            if (id != null) {
                slots[idx] = makeLegacyContents(id, slotTag.getInt("count"));
            }
        }
    }

    // ── Sync (for client-side renderer) ──────────────────────────────────────

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean canMerge(SkeinContents slot, SkeinContents addition) {
        return slot.mergesWith(addition) && !slot.variants().isEmpty() && !addition.variants().isEmpty();
    }

    private static SkeinContents makeContents(@Nullable SkeinContents source, ResourceLocation itemId, int amount) {
        int tier = source != null ? source.needleTier() : ItemTierRegistry.getTierForItem(itemId);
        int displayColor = source != null ? source.displayColor() : colorFromId(itemId.toString());
        String groupKey = rackGroupKey(tier, displayColor);
        int complexityScore = source != null ? source.complexityScore() : 0;
        Optional<java.util.UUID> dataRef = source != null ? source.dataRef() : Optional.empty();

        return new SkeinContents(
            tier,
            groupKey,
            amount,
            List.of(new SkeinContents.VariantEntry(itemId, amount)),
            displayColor,
            complexityScore,
            dataRef
        );
    }

    private static SkeinContents makeLegacyContents(ResourceLocation itemId, int amount) {
        return makeContents(null, itemId, amount);
    }

    private static String rackGroupKey(int tier, int displayColor) {
        return "rack:" + tier + ":" + Integer.toHexString(displayColor & 0xFFFFFF);
    }

    private static ItemStack createSkeinStack(SkeinContents contents) {
        ItemStack stack = new ItemStack(MusubiItems.SKEIN.get());
        stack.set(MusubiDataComponents.SKEIN_CONTENTS, contents);
        return stack;
    }

    private static CompoundTag saveContents(SkeinContents contents) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("needle_tier", contents.needleTier());
        tag.putString("group_key", contents.groupKey());
        tag.putInt("count", contents.count());
        tag.putInt("display_color", contents.displayColor());
        tag.putInt("complexity_score", contents.complexityScore());
        contents.dataRef().ifPresent(id -> tag.putUUID("data_ref", id));

        ListTag variants = new ListTag();
        for (SkeinContents.VariantEntry entry : contents.variants()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("item", entry.itemId().toString());
            entryTag.putInt("count", entry.count());
            variants.add(entryTag);
        }
        tag.put("variants", variants);
        return tag;
    }

    private static SkeinContents loadContents(CompoundTag tag) {
        List<SkeinContents.VariantEntry> variants = new ArrayList<>();
        ListTag variantTags = tag.getList("variants", Tag.TAG_COMPOUND);
        for (int i = 0; i < variantTags.size(); i++) {
            CompoundTag entryTag = variantTags.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entryTag.getString("item"));
            if (id != null) {
                variants.add(new SkeinContents.VariantEntry(id, entryTag.getInt("count")));
            }
        }

        int total = tag.getInt("count");
        if (total <= 0) {
            total = variants.stream().mapToInt(SkeinContents.VariantEntry::count).sum();
        }
        return new SkeinContents(
            tag.getInt("needle_tier"),
            tag.getString("group_key"),
            total,
            List.copyOf(variants),
            tag.getInt("display_color"),
            tag.getInt("complexity_score"),
            tag.hasUUID("data_ref") ? Optional.of(tag.getUUID("data_ref")) : Optional.empty()
        );
    }

    private static int colorFromId(String id) {
        int h = id.hashCode();
        int r = Math.max(0x40, (h >>> 16) & 0xFF);
        int g = Math.max(0x40, (h >>> 8) & 0xFF);
        int b = Math.max(0x40, h & 0xFF);
        return (r << 16) | (g << 8) | b;
    }
}
