package com.sanhiruzu.musubi.blockentity;

import com.sanhiruzu.musubi.block.MarudaiBlock;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.item.MusubiItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Handles the winding operation: takes a mounted skein and routes each item variant
 * to the best-matching nearby spool rack, one item every WIND_INTERVAL ticks.
 *
 * Racks are discovered in a DISCOVERY_RADIUS cube when a skein is mounted. If no
 * racks are found at that moment, mounting is rejected and the skein stays in hand.
 *
 * If a variant cannot be deposited (all nearby racks full for that item type), the
 * item is dropped as an entity at the marudai rather than silently lost.
 */
public class MarudaiBlockEntity extends BlockEntity {

    /** Ticks between each individual item being wound onto a rack (~10 items/second). */
    public static final int WIND_INTERVAL = 2;
    /** Block radius scanned for spool racks when a skein is mounted. */
    private static final int DISCOVERY_RADIUS = 4;

    // ── Winding state ─────────────────────────────────────────────────────────

    /** The skein currently being wound. Null when idle. */
    @Nullable private SkeinContents mountedSkein;
    /** Remaining variants to wind, drained as items are deposited. */
    private final List<SkeinContents.VariantEntry> pendingVariants = new ArrayList<>();
    /** Cached rack positions discovered when winding began. */
    private final List<BlockPos> targetRacks = new ArrayList<>();
    /** Countdown to the next wind operation. */
    private int tickTimer = 0;
    /** Set on load; triggers one rack re-discovery before the first wind tick. */
    private boolean needsRackRediscovery = false;

    public MarudaiBlockEntity(BlockPos pos, BlockState state) {
        super(MusubiBlockEntities.MARUDAI.get(), pos, state);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isWinding() {
        return mountedSkein != null;
    }

    /**
     * Attempts to mount a skein and begin winding.
     * Returns false (and does nothing) if already winding or no racks are reachable.
     */
    public boolean mountSkein(SkeinContents contents) {
        if (mountedSkein != null) return false;
        List<BlockPos> racks = discoverNearbyRacks();
        if (racks.isEmpty()) return false;

        mountedSkein = contents;
        pendingVariants.clear();
        pendingVariants.addAll(contents.variants());
        targetRacks.clear();
        targetRacks.addAll(racks);
        tickTimer = 0;
        setChanged();
        return true;
    }

    /**
     * Removes and returns a partial skein representing whatever has not yet been wound.
     * Called by MarudaiBlock.onRemove so contents survive a block break.
     * Returns null if idle or fully wound.
     */
    @Nullable
    public ItemStack takeMountedSkein() {
        if (mountedSkein == null || pendingVariants.isEmpty()) {
            mountedSkein = null;
            return null;
        }
        int remaining = pendingVariants.stream().mapToInt(SkeinContents.VariantEntry::count).sum();
        SkeinContents partial = new SkeinContents(
            mountedSkein.needleTier(), mountedSkein.groupKey(), remaining,
            List.copyOf(pendingVariants), mountedSkein.displayColor(),
            mountedSkein.complexityScore(), mountedSkein.dataRef()
        );
        ItemStack stack = new ItemStack(MusubiItems.SKEIN.get());
        stack.set(MusubiDataComponents.SKEIN_CONTENTS, partial);
        mountedSkein = null;
        pendingVariants.clear();
        return stack;
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public static void tick(Level level, BlockPos pos, BlockState state, MarudaiBlockEntity be) {
        if (level.isClientSide) return;
        be.serverTick(level, pos, state);
    }

    private void serverTick(Level level, BlockPos pos, BlockState state) {
        if (mountedSkein == null) return;

        if (needsRackRediscovery) {
            targetRacks.clear();
            targetRacks.addAll(discoverNearbyRacks());
            needsRackRediscovery = false;
        }

        if (tickTimer > 0) {
            tickTimer--;
            return;
        }

        if (pendingVariants.isEmpty()) {
            finishWinding(level, pos, state);
            return;
        }

        windNextItem(level, pos);
        tickTimer = WIND_INTERVAL;
    }

    /**
     * Winds one item from the head of the pending list.
     * Tries each rack in discovery order; drops the item if no rack has space.
     */
    private void windNextItem(Level level, BlockPos pos) {
        SkeinContents.VariantEntry head = pendingVariants.get(0);
        boolean deposited = false;

        for (BlockPos rackPos : targetRacks) {
            if (level.getBlockEntity(rackPos) instanceof SpoolRackBlockEntity rack) {
                if (rack.tryDeposit(head.itemId(), 1) > 0) {
                    deposited = true;
                    break;
                }
            }
        }

        if (!deposited) {
            // All reachable racks are full for this item type — drop it rather than lose it.
            ItemStack drop = new ItemStack(
                BuiltInRegistries.ITEM.getOptional(head.itemId()).orElse(Items.AIR), 1);
            if (!drop.isEmpty()) {
                level.addFreshEntity(new ItemEntity(
                    level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, drop));
            }
        }

        // Advance or remove the head variant.
        if (head.count() > 1) {
            pendingVariants.set(0, new SkeinContents.VariantEntry(head.itemId(), head.count() - 1));
        } else {
            pendingVariants.remove(0);
        }
        setChanged();
    }

    private void finishWinding(Level level, BlockPos pos, BlockState state) {
        mountedSkein = null;
        pendingVariants.clear();
        targetRacks.clear();
        setChanged();
        // Clear WINDING flag on the block state.
        if (state.getBlock() instanceof MarudaiBlock && state.getValue(MarudaiBlock.WINDING)) {
            level.setBlock(pos, state.setValue(MarudaiBlock.WINDING, false), 3);
        }
    }

    // ── Rack discovery ────────────────────────────────────────────────────────

    private List<BlockPos> discoverNearbyRacks() {
        List<BlockPos> racks = new ArrayList<>();
        if (level == null) return racks;
        for (int dx = -DISCOVERY_RADIUS; dx <= DISCOVERY_RADIUS; dx++) {
            for (int dy = -DISCOVERY_RADIUS; dy <= DISCOVERY_RADIUS; dy++) {
                for (int dz = -DISCOVERY_RADIUS; dz <= DISCOVERY_RADIUS; dz++) {
                    BlockPos candidate = worldPosition.offset(dx, dy, dz);
                    if (level.getBlockEntity(candidate) instanceof SpoolRackBlockEntity) {
                        racks.add(candidate.immutable());
                    }
                }
            }
        }
        return racks;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (mountedSkein == null) return;

        tag.putBoolean("winding", true);
        tag.putInt("needle_tier", mountedSkein.needleTier());
        tag.putString("group_key", mountedSkein.groupKey());
        tag.putInt("display_color", mountedSkein.displayColor());
        tag.putInt("complexity_score", mountedSkein.complexityScore());

        ListTag variants = new ListTag();
        for (SkeinContents.VariantEntry v : pendingVariants) {
            CompoundTag vTag = new CompoundTag();
            vTag.putString("item", v.itemId().toString());
            vTag.putInt("count", v.count());
            variants.add(vTag);
        }
        tag.put("pending_variants", variants);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mountedSkein = null;
        pendingVariants.clear();
        targetRacks.clear();

        if (!tag.getBoolean("winding")) return;

        ListTag variants = tag.getList("pending_variants", 10);
        for (int i = 0; i < variants.size(); i++) {
            CompoundTag vTag = variants.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(vTag.getString("item"));
            if (id != null) {
                pendingVariants.add(new SkeinContents.VariantEntry(id, vTag.getInt("count")));
            }
        }
        int total = pendingVariants.stream().mapToInt(SkeinContents.VariantEntry::count).sum();
        mountedSkein = new SkeinContents(
            tag.getInt("needle_tier"),
            tag.getString("group_key"),
            total,
            List.copyOf(pendingVariants),
            tag.getInt("display_color"),
            tag.getInt("complexity_score"),
            Optional.empty()
        );
        // targetRacks is re-discovered on the first tick after load (level is not yet
        // available during loadAdditional, so discovery is deferred via the flag).
        needsRackRediscovery = true;
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

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
