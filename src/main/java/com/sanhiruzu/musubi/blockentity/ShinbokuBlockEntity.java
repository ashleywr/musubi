package com.sanhiruzu.musubi.blockentity;

import com.sanhiruzu.musubi.item.MusubiItems;
import com.sanhiruzu.musubi.rei.IReiStorage;
import com.sanhiruzu.musubi.rei.ReiUtil;
import com.sanhiruzu.musubi.rei.SimpleReiStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A Shinboku (神木) acts as a sponge for ambient natural REI.
 *
 * The amount absorbed per tick depends on:
 *  - The wrapped block's spiritual resonance (log, stone, or crystal).
 *  - The density of naturally-occurring blocks in the surrounding 8-block radius,
 *    sampled every 10 seconds. An untouched forest or cave earns a large bonus;
 *    a cleared quarry earns nothing.
 *
 * REI rate = baseRate + naturalBonus
 *   base:  log = 4, stone = 6, amethyst/crystal = 10, other = 2
 *   bonus: +1 per 50 "natural" blocks within 8 blocks, capped at +10
 *
 * Total maximum: 20 REI/t (amethyst in pristine nature).
 */
public class ShinbokuBlockEntity extends BlockEntity {

    public static final int CAPACITY       = 2000;
    public static final int SCAN_RANGE     = 8;
    public static final int SCAN_EVERY     = 200; // 10 seconds
    public static final int DISTRIBUTE_EVERY = 20;

    private final SimpleReiStorage rei = new SimpleReiStorage(CAPACITY);

    @Nullable private ResourceLocation wrappedBlockId = null;
    private int baseRate     = 2;
    private int naturalBonus = 0;
    private int scanTimer     = SCAN_EVERY;
    private int distributeTimer = DISTRIBUTE_EVERY;

    public ShinbokuBlockEntity(BlockPos pos, BlockState state) {
        super(MusubiBlockEntities.SHINBOKU.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ShinbokuBlockEntity be) {
        // Periodic chunk-richness scan.
        be.scanTimer--;
        if (be.scanTimer <= 0) {
            be.scanTimer     = SCAN_EVERY;
            be.naturalBonus  = scanNaturalRichness(level, pos);
        }

        // Absorb REI from surrounding nature each tick.
        be.rei.generate(be.baseRate + be.naturalBonus);

        // Distribute to adjacent blocks every second.
        be.distributeTimer--;
        if (be.distributeTimer <= 0) {
            be.distributeTimer = DISTRIBUTE_EVERY;
            ReiUtil.distribute(be.rei, level, pos);
        }

        if (be.rei.isDirty()) {
            be.rei.clearDirty();
            be.setChanged();
        }
    }

    /** Scans for naturally-occurring blocks in the surrounding radius.
     *  Returns a bonus REI/t value (0–10). */
    private static int scanNaturalRichness(Level level, BlockPos origin) {
        int count = 0;
        for (BlockPos p : BlockPos.betweenClosed(
                origin.offset(-SCAN_RANGE, -SCAN_RANGE, -SCAN_RANGE),
                origin.offset( SCAN_RANGE,  SCAN_RANGE,  SCAN_RANGE))) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.LOGS)
                || s.is(BlockTags.LEAVES)
                || s.is(BlockTags.FLOWERS)
                || s.is(BlockTags.DIRT)
                || s.is(BlockTags.SAND)
                || s.is(BlockTags.STONE_ORE_REPLACEABLES)
                || s.is(BlockTags.CRYSTAL_SOUND_BLOCKS)) {
                count++;
            }
        }
        return Math.min(count / 50, 10);
    }

    // ── Wrapped-block management ───────────────────────────────────────────────

    public void setWrappedBlock(BlockState state) {
        wrappedBlockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        baseRate       = computeBaseRate(state);
        setChanged();
    }

    @Nullable
    public ResourceLocation getWrappedBlockId() { return wrappedBlockId; }

    private static int computeBaseRate(BlockState state) {
        if (state.is(BlockTags.CRYSTAL_SOUND_BLOCKS)) return 10;
        if (state.is(BlockTags.LOGS))                 return 4;
        if (state.is(BlockTags.STONE_ORE_REPLACEABLES)) return 6;
        return 2;
    }

    /** Called by ShinbokuBlock.onRemove — drops the wrapped item and the Shimenawa. */
    public void dropContents(Level level, BlockPos pos) {
        if (wrappedBlockId != null) {
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(wrappedBlockId);
            block.ifPresent(b -> {
                Item item = b.asItem();
                if (item != Items.AIR) {
                    Block.popResource(level, pos, new ItemStack(item));
                }
            });
        }
        Block.popResource(level, pos, new ItemStack(MusubiItems.SHIMENAWA.get()));
    }

    public IReiStorage getReiStorage() { return rei; }
    public int getBaseRate()    { return baseRate; }
    public int getNaturalBonus(){ return naturalBonus; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("rei", rei.getRaw());
        if (wrappedBlockId != null) tag.putString("wrapped_block", wrappedBlockId.toString());
        tag.putInt("base_rate",    baseRate);
        tag.putInt("natural_bonus", naturalBonus);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rei.setRaw(tag.getInt("rei"));
        if (tag.contains("wrapped_block")) {
            wrappedBlockId = ResourceLocation.parse(tag.getString("wrapped_block"));
            // Recompute base rate from the stored block's default state.
            BuiltInRegistries.BLOCK.getOptional(wrappedBlockId)
                .ifPresent(b -> baseRate = computeBaseRate(b.defaultBlockState()));
        }
        naturalBonus = tag.getInt("natural_bonus");
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
