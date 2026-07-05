package com.sanhiruzu.musubi.blockentity;

import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.rei.SimpleReiStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hikidashi (引き出し) — queries nearby Spool Racks and provides a GUI
 * for searching, sorting, and extracting colored silk threads.
 *
 * <p>Scans within SEARCH_RANGE blocks for all connected Spool Racks and
 * caches their locations. The cached list refreshes every CACHE_TICKS.
 */
public class HikidashiBlockEntity extends BlockEntity {

    public static final int SEARCH_RANGE = 8;
    private static final int CACHE_TICKS = 20; // Refresh rack list every second
    private static final int REI_CAPACITY = 1000;
    private static final int REI_COST_PER_EXTRACT = 10; // REI consumed per extraction
    private static final int DECAY_TICKS = 1200; // Decay every minute (20 ticks/sec * 60 sec)
    private static final int REI_DECAY_IDLE = 5; // REI lost per decay tick when idle
    private static final int REI_DECAY_ACTIVE = 8; // REI lost per decay tick after extraction

    private List<BlockPos> cachedRackPositions = new ArrayList<>();
    private int cacheTimer = 0;
    private final SimpleReiStorage reiStorage = new SimpleReiStorage(REI_CAPACITY);
    private int decayTimer = DECAY_TICKS;
    private int corruption = 0; // 0-100, tracks miasma buildup
    private int ticksSinceExtraction = 0; // Track if recently used

    public HikidashiBlockEntity(BlockPos pos, BlockState state) {
        super(MusubiBlockEntities.HIKIDASHI.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HikidashiBlockEntity be) {
        if (level == null || level.isClientSide) return;

        be.decayTimer--;
        be.ticksSinceExtraction++;

        // Decay REI and build corruption
        if (be.decayTimer <= 0) {
            be.decayTimer = DECAY_TICKS;

            boolean wasRecentlyUsed = be.ticksSinceExtraction < 100; // 5 seconds
            int decayAmount = wasRecentlyUsed ? REI_DECAY_ACTIVE : REI_DECAY_IDLE;

            // Lose REI
            int lost = Math.min(be.reiStorage.getREI(), decayAmount);
            be.reiStorage.extractREI(lost, false);

            // Build corruption equal to REI lost
            be.corruption = Math.min(100, be.corruption + lost);

            // Spawn miasma particles if corrupted
            if (be.corruption > 20) {
                spawnMiasmaParticles(level, pos, be.corruption);
            }

            be.setChanged();
        }
    }

    private static void spawnMiasmaParticles(Level level, BlockPos pos, int corruption) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        int particleCount = Math.min(5, corruption / 20);
        for (int i = 0; i < particleCount; i++) {
            double x = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 1.5;
            double y = pos.getY() + 0.5 + (level.random.nextDouble() - 0.5) * 1.5;
            double z = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 1.5;

            net.minecraft.core.particles.ParticleOptions particle =
                new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(0.6f, 0.2f, 0.8f), // Purple
                    1.0f
                );
            serverLevel.sendParticles(particle, x, y, z, 1, 0, 0, 0, 0.02);
        }
    }

    /**
     * Purify the area with a bell, clearing corruption.
     */
    public void purify() {
        this.corruption = 0;
        this.setChanged();
    }

    public int getCorruption() {
        return corruption;
    }

    /**
     * Queries nearby Spool Racks and returns a list of all their SkeinContents.
     * The list is sorted by tier, then by item name.
     */
    public List<SkeinContents> queryStorage() {
        Level level = getLevel();
        if (level == null) return List.of();

        List<SkeinContents> all = new ArrayList<>();
        for (BlockPos rackPos : getCachedRackPositions()) {
            BlockEntity be = level.getBlockEntity(rackPos);
            if (be instanceof SpoolRackBlockEntity rack) {
                rack.addAllContents(all);
            }
        }

        // Sort by tier (ascending), then by item name
        all.sort((a, b) -> {
            int tierCmp = Integer.compare(a.needleTier(), b.needleTier());
            if (tierCmp != 0) return tierCmp;
            String nameA = a.variants().isEmpty() ? "" : a.variants().get(0).itemId().toString();
            String nameB = b.variants().isEmpty() ? "" : b.variants().get(0).itemId().toString();
            return nameA.compareTo(nameB);
        });

        return all;
    }

    /**
     * Scans for nearby Spool Racks and caches their positions.
     */
    private List<BlockPos> getCachedRackPositions() {
        Level level = getLevel();
        if (level == null) return cachedRackPositions;

        cacheTimer--;
        if (cacheTimer <= 0) {
            cacheTimer = CACHE_TICKS;
            cachedRackPositions.clear();

            BlockPos origin = getBlockPos();
            for (BlockPos p : BlockPos.betweenClosed(
                    origin.offset(-SEARCH_RANGE, -SEARCH_RANGE, -SEARCH_RANGE),
                    origin.offset(SEARCH_RANGE, SEARCH_RANGE, SEARCH_RANGE))) {
                BlockEntity be = level.getBlockEntity(p);
                if (be instanceof SpoolRackBlockEntity) {
                    cachedRackPositions.add(p.immutable());
                }
            }
        }

        return cachedRackPositions;
    }

    /**
     * Opens the storage GUI for the player.
     */
    public void openGui(Player player) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && getLevel() instanceof ServerLevel) {
            List<SkeinContents> storage = queryStorage();
            var payload = new com.sanhiruzu.musubi.network.OpenHikidashiScreenPayload(
                getBlockPos(), storage);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer, payload);
        }
    }

    /**
     * Attempts to extract the given amount of the specified item from connected racks.
     * Consumes REI energy for the extraction. Returns the number of items actually extracted.
     */
    public int extractItem(net.minecraft.resources.ResourceLocation itemId, int amount) {
        Level level = getLevel();
        if (level == null || level.isClientSide) return 0;

        // Check if we have enough REI
        if (!reiStorage.consume(REI_COST_PER_EXTRACT)) {
            return 0; // Not enough energy
        }

        this.ticksSinceExtraction = 0; // Mark as recently used

        int extracted = 0;
        for (BlockPos rackPos : getCachedRackPositions()) {
            BlockEntity be = level.getBlockEntity(rackPos);
            if (be instanceof SpoolRackBlockEntity rack) {
                int fromRack = rack.tryExtract(itemId, amount - extracted);
                extracted += fromRack;
                if (extracted >= amount) break;
            }
        }

        if (extracted > 0) {
            setChanged();
        }

        return extracted;
    }

    /**
     * Gets the REI storage for this Hikidashi.
     */
    public SimpleReiStorage getReiStorage() {
        return reiStorage;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag rackList = new ListTag();
        for (BlockPos pos : cachedRackPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putLong("pos", pos.asLong());
            rackList.add(posTag);
        }
        tag.put("cached_racks", rackList);
        tag.putInt("rei", reiStorage.getRaw());
        tag.putInt("corruption", corruption);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cachedRackPositions.clear();
        ListTag rackList = tag.getList("cached_racks", Tag.TAG_COMPOUND);
        for (int i = 0; i < rackList.size(); i++) {
            long posLong = rackList.getCompound(i).getLong("pos");
            cachedRackPositions.add(BlockPos.of(posLong));
        }
        reiStorage.setRaw(tag.getInt("rei"));
        corruption = tag.getInt("corruption");
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
