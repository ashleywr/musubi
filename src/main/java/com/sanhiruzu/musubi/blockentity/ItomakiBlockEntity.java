package com.sanhiruzu.musubi.blockentity;

import com.sanhiruzu.musubi.block.ItomakiBlock;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.component.TamaStorage;
import com.sanhiruzu.musubi.sound.MusubiSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The Itomaki (糸巻き) — set a Tama on this block to unspool its contents
 * onto nearby Spool Racks automatically.
 *
 * Every TICKS_PER_BATCH ticks it deposits up to ITEMS_PER_BATCH of one item
 * type into the first accepting Spool Rack within SEARCH_RANGE. It cycles
 * through item types so no single type can block others indefinitely.
 */
public class ItomakiBlockEntity extends BlockEntity {

    public static final int TICKS_PER_BATCH = 2;
    public static final int ITEMS_PER_BATCH = 64;
    public static final int SEARCH_RANGE    = 5;

    private ItemStack tama = ItemStack.EMPTY;
    private int batchTimer = TICKS_PER_BATCH;
    private int soundTimer = 0;

    public ItomakiBlockEntity(BlockPos pos, BlockState state) {
        super(MusubiBlockEntities.ITOMAKI.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ItomakiBlockEntity be) {
        if (be.tama.isEmpty()) return;

        TamaStorage storage = be.tama.getOrDefault(MusubiDataComponents.TAMA_STORAGE, TamaStorage.EMPTY);
        if (storage.isEmpty()) {
            // Done — Tama is fully unspooled; leave it for the player to collect.
            if (state.getValue(ItomakiBlock.SPOOLING)) {
                level.setBlock(pos, state.setValue(ItomakiBlock.SPOOLING, false), 3);
            }
            return;
        }

        be.batchTimer--;
        if (be.batchTimer > 0) return;
        be.batchTimer = TICKS_PER_BATCH;

        boolean deposited = be.unspool(level, pos, state, storage);
        if (deposited) {
            be.soundTimer--;
            if (be.soundTimer <= 0) {
                be.soundTimer = 20;
                level.playSound(null, pos, MusubiSounds.RACK_DEPOSIT.get(),
                    SoundSource.BLOCKS, 0.4f, 1.1f);
            }
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    /**
     * Tries each entry in TamaStorage in order, depositing the first type
     * that fits into any nearby Spool Rack. Returns true if anything moved.
     */
    private boolean unspool(Level level, BlockPos pos, BlockState state, TamaStorage storage) {
        List<SkeinContents.VariantEntry> entries = new ArrayList<>(storage.entries());

        for (int i = 0; i < entries.size(); i++) {
            SkeinContents.VariantEntry entry = entries.get(i);
            int toDeposit = Math.min(entry.count(), ITEMS_PER_BATCH);
            int deposited  = tryDepositToNearbyRack(level, pos, entry.itemId(), toDeposit);
            if (deposited <= 0) continue;

            int remaining = entry.count() - deposited;
            if (remaining <= 0) {
                entries.remove(i);
            } else {
                entries.set(i, new SkeinContents.VariantEntry(entry.itemId(), remaining));
            }
            tama.set(MusubiDataComponents.TAMA_STORAGE, new TamaStorage(List.copyOf(entries)));
            return true;
        }
        return false;
    }

    /** Finds the nearest Spool Rack that can take {@code itemId} and deposits up to {@code amount}. */
    private int tryDepositToNearbyRack(Level level, BlockPos origin, ResourceLocation itemId, int amount) {
        // Prefer racks that already hold this item type (more cache-friendly).
        int bestFit = 0;
        SpoolRackBlockEntity bestRack = null;

        for (BlockPos p : BlockPos.betweenClosed(
                origin.offset(-SEARCH_RANGE, -SEARCH_RANGE, -SEARCH_RANGE),
                origin.offset( SEARCH_RANGE,  SEARCH_RANGE,  SEARCH_RANGE))) {
            if (!(level.getBlockEntity(p) instanceof SpoolRackBlockEntity rack)) continue;
            if (!rack.canAccept(itemId)) continue;
            // Among acceptable racks, prefer one already holding this type.
            int score = rack.getSlotForItem(itemId) >= 0 ? 2 : 1;
            if (score > bestFit) {
                bestFit = score;
                bestRack = rack;
            }
        }

        return bestRack != null ? bestRack.tryDeposit(itemId, amount) : 0;
    }

    // ── Tama management ──────────────────────────────────────────────────────

    public boolean hasTama()    { return !tama.isEmpty(); }
    public ItemStack getTama()  { return tama; }

    /** Inserts a Tama; returns false if slot already occupied. */
    public boolean insertTama(ItemStack stack) {
        if (!tama.isEmpty()) return false;
        tama = stack.copyWithCount(1);
        stack.shrink(1);
        setChanged();
        return true;
    }

    /** Removes and returns the Tama (empty if none). */
    public ItemStack retrieveTama() {
        if (tama.isEmpty()) return ItemStack.EMPTY;
        ItemStack out = tama;
        tama = ItemStack.EMPTY;
        setChanged();
        return out;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!tama.isEmpty()) tag.put("tama", tama.save(registries));
        tag.putInt("batch_timer", batchTimer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tama        = tag.contains("tama") ? ItemStack.parseOptional(registries, tag.getCompound("tama")) : ItemStack.EMPTY;
        batchTimer  = tag.getInt("batch_timer");
    }

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
