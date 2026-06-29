package com.sanhiruzu.musubi.blockentity;

import com.sanhiruzu.musubi.component.SkeinContents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Four independent item slots, each capped at SLOT_CAPACITY items of one type.
 *
 * Items are stored as (ResourceLocation, count) pairs — no DataComponent payload.
 * Complex items (enchanted tools, etc.) should not be sewn; that path is guarded
 * upstream by complexityScore in SkeinContents.
 *
 * Extraction:
 *   right-click      → one stack (up to 64) from the first non-empty slot
 *   shift-right-click → all items from all slots, given to player or dropped
 */
public class SpoolRackBlockEntity extends BlockEntity {

    public static final int SLOTS = 4;
    public static final int SLOT_CAPACITY = 2048;

    private final ResourceLocation[] itemIds = new ResourceLocation[SLOTS];
    private final int[] counts = new int[SLOTS];

    public SpoolRackBlockEntity(BlockPos pos, BlockState state) {
        super(MusubiBlockEntities.SPOOL_RACK.get(), pos, state);
    }

    // ── Deposit ───────────────────────────────────────────────────────────────

    /**
     * Deposits as many items from the skein as the rack can hold.
     * Returns null if the skein was fully consumed, or a new SkeinContents
     * containing only the variants that did not fit.
     */
    @Nullable
    public SkeinContents depositSkein(SkeinContents contents) {
        List<SkeinContents.VariantEntry> remaining = new ArrayList<>();
        for (SkeinContents.VariantEntry entry : contents.variants()) {
            int deposited = depositItem(entry.itemId(), entry.count());
            int leftover = entry.count() - deposited;
            if (leftover > 0) {
                remaining.add(new SkeinContents.VariantEntry(entry.itemId(), leftover));
            }
        }
        setChanged();
        if (remaining.isEmpty()) return null;
        int remainingCount = remaining.stream().mapToInt(SkeinContents.VariantEntry::count).sum();
        return new SkeinContents(
            contents.needleTier(), contents.groupKey(), remainingCount,
            List.copyOf(remaining), contents.displayColor(),
            contents.complexityScore(), contents.dataRef()
        );
    }

    /**
     * Public entry-point for the Marudai to deposit individual items one at a time.
     * Returns the number actually deposited (0 if no space).
     */
    public int tryDeposit(ResourceLocation itemId, int amount) {
        int deposited = depositItem(itemId, amount);
        if (deposited > 0) setChanged();
        return deposited;
    }

    /**
     * Deposits up to {@code amount} items of the given type.
     * Returns the number actually deposited (may be less than amount if the rack fills).
     */
    private int depositItem(ResourceLocation itemId, int amount) {
        // Prefer an existing slot for this item type.
        for (int i = 0; i < SLOTS; i++) {
            if (itemId.equals(itemIds[i])) {
                int space = SLOT_CAPACITY - counts[i];
                int deposited = Math.min(space, amount);
                counts[i] += deposited;
                return deposited;
            }
        }
        // Fall back to the first empty slot.
        for (int i = 0; i < SLOTS; i++) {
            if (itemIds[i] == null) {
                int deposited = Math.min(SLOT_CAPACITY, amount);
                itemIds[i] = itemId;
                counts[i] = deposited;
                return deposited;
            }
        }
        return 0;
    }

    // ── Extraction ────────────────────────────────────────────────────────────

    /**
     * Gives the player one stack (up to 64) from the first non-empty slot.
     */
    public void extractOne(Player player) {
        for (int i = 0; i < SLOTS; i++) {
            if (itemIds[i] != null && counts[i] > 0) {
                Item item = BuiltInRegistries.ITEM.getOptional(itemIds[i]).orElse(Items.AIR);
                if (item == Items.AIR) {
                    clearSlot(i);
                    continue;
                }
                int amount = Math.min(64, counts[i]);
                give(player, new ItemStack(item, amount));
                counts[i] -= amount;
                if (counts[i] == 0) clearSlot(i);
                setChanged();
                break;
            }
        }
    }

    /**
     * Gives the player all items from all slots, batch-split at 64 per stack.
     */
    public void extractAll(Player player) {
        for (int i = 0; i < SLOTS; i++) {
            if (itemIds[i] == null || counts[i] == 0) continue;
            Item item = BuiltInRegistries.ITEM.getOptional(itemIds[i]).orElse(Items.AIR);
            if (item != Items.AIR) {
                int remaining = counts[i];
                while (remaining > 0) {
                    int batch = Math.min(64, remaining);
                    give(player, new ItemStack(item, batch));
                    remaining -= batch;
                }
            }
            clearSlot(i);
        }
        setChanged();
    }

    private void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private void clearSlot(int i) {
        itemIds[i] = null;
        counts[i] = 0;
    }

    /**
     * Drops all stored items as item entities at the given position.
     * Called by SpoolRackBlock.onRemove so contents are never silently lost.
     */
    public void dropContents(net.minecraft.world.level.Level level, BlockPos pos) {
        for (int i = 0; i < SLOTS; i++) {
            if (itemIds[i] == null || counts[i] == 0) continue;
            Item item = BuiltInRegistries.ITEM.getOptional(itemIds[i]).orElse(Items.AIR);
            if (item != Items.AIR) {
                int remaining = counts[i];
                while (remaining > 0) {
                    int batch = Math.min(64, remaining);
                    net.minecraft.world.entity.item.ItemEntity entity =
                        new net.minecraft.world.entity.item.ItemEntity(
                            level,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            new ItemStack(item, batch)
                        );
                    level.addFreshEntity(entity);
                    remaining -= batch;
                }
            }
            clearSlot(i);
        }
    }

    // ── Read-only accessors (for future renderer / tooltip) ───────────────────

    public @Nullable ResourceLocation getSlotItem(int slot) { return itemIds[slot]; }
    public int getSlotCount(int slot) { return counts[slot]; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag slots = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            if (itemIds[i] != null && counts[i] > 0) {
                CompoundTag slot = new CompoundTag();
                slot.putInt("slot", i);
                slot.putString("item", itemIds[i].toString());
                slot.putInt("count", counts[i]);
                slots.add(slot);
            }
        }
        tag.put("slots", slots);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Arrays.fill(itemIds, null);
        Arrays.fill(counts, 0);
        ListTag slots = tag.getList("slots", 10);
        for (int i = 0; i < slots.size(); i++) {
            CompoundTag slot = slots.getCompound(i);
            int idx = slot.getInt("slot");
            if (idx >= 0 && idx < SLOTS) {
                ResourceLocation id = ResourceLocation.tryParse(slot.getString("item"));
                if (id != null) {
                    itemIds[idx] = id;
                    counts[idx] = slot.getInt("count");
                }
            }
        }
    }

    // ── Sync (for future client-side renderer) ────────────────────────────────

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
}
