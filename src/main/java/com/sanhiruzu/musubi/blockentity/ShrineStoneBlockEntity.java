package com.sanhiruzu.musubi.blockentity;

import com.sanhiruzu.musubi.rei.IReiStorage;
import com.sanhiruzu.musubi.rei.ReiUtil;
import com.sanhiruzu.musubi.rei.SimpleReiStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Passively generates REI (Reiryoku) every tick.
 * Base rate: 3 REI/t. Bonus: +2 REI/t for each occupied Spool Rack
 * within 5 blocks (up to +10), rewarding a well-ordered silk space.
 *
 * When blessed by a Gohei the base rate doubles for the duration.
 *
 * Distributes REI to adjacent blocks every 20 ticks.
 */
public class ShrineStoneBlockEntity extends BlockEntity {

    public static final int CAPACITY    = 1000;
    public static final int BASE_RATE   = 3;
    public static final int BONUS_RATE  = 2;
    public static final int BONUS_RANGE = 5;
    public static final int MAX_BONUS   = 10;

    private static final int DISTRIBUTE_EVERY = 20;

    private final SimpleReiStorage rei = new SimpleReiStorage(CAPACITY);
    private int distributeTimer = DISTRIBUTE_EVERY;
    private int blessedTimer    = 0;
    private int rackBonus       = 0;

    public ShrineStoneBlockEntity(BlockPos pos, BlockState state) {
        super(MusubiBlockEntities.SHRINE_STONE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ShrineStoneBlockEntity be) {
        // Blessed status counts down each tick.
        if (be.blessedTimer > 0) be.blessedTimer--;

        // Generate REI each tick; blessed doubles the base rate.
        int base  = be.blessedTimer > 0 ? BASE_RATE * 2 : BASE_RATE;
        be.rackBonus = Math.min(MAX_BONUS, countNearbyRacks(level, pos) * BONUS_RATE);
        be.rei.generate(base + be.rackBonus);

        // Distribute to neighbors once per second.
        be.distributeTimer--;
        if (be.distributeTimer <= 0) {
            be.distributeTimer = DISTRIBUTE_EVERY;
            ReiUtil.distribute(be.rei, level, pos);
        }

        if (be.rei.isDirty()) {
            be.rei.clearDirty();
            be.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private static int countNearbyRacks(Level level, BlockPos origin) {
        int count = 0;
        for (BlockPos p : BlockPos.betweenClosed(
                origin.offset(-BONUS_RANGE, -BONUS_RANGE, -BONUS_RANGE),
                origin.offset( BONUS_RANGE,  BONUS_RANGE,  BONUS_RANGE))) {
            if (level.getBlockEntity(p) instanceof SpoolRackBlockEntity rack) {
                for (int i = 0; i < SpoolRackBlockEntity.SLOTS; i++) {
                    if (rack.getSlotCount(i) > 0) { count++; break; }
                }
            }
        }
        return count;
    }

    /** Sets a blessing timer (ticks). If already blessed, extends it. */
    public void bless(int ticks) {
        blessedTimer = Math.max(blessedTimer, ticks);
        setChanged();
    }

    public boolean isBlessed()      { return blessedTimer > 0; }
    public int getBlessedTimer()    { return blessedTimer; }
    public int getRackBonus()       { return rackBonus; }
    public IReiStorage getReiStorage() { return rei; }
    public int getREI()    { return rei.getREI(); }
    public int getMaxREI() { return rei.getMaxREI(); }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("rei",          rei.getRaw());
        tag.putInt("blessed_timer", blessedTimer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rei.setRaw(tag.getInt("rei"));
        blessedTimer = tag.getInt("blessed_timer");
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
