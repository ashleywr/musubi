package com.sanhiruzu.musubi.blockentity;

import com.sanhiruzu.musubi.item.MusubiItems;
import com.sanhiruzu.musubi.rei.IReiStorage;
import com.sanhiruzu.musubi.rei.ReiUtil;
import com.sanhiruzu.musubi.rei.SimpleReiStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A Shinboku (神木) acts as a sponge for ambient natural REI.
 *
 * REI rate = baseRate + naturalBonus (sampled every 10 s from an 8-block radius).
 *   base:  amethyst/crystal = 10,  stone variants = 6,  log = 4,  other = 2
 *   bonus: +1 per 50 "natural" blocks (logs, leaves, flowers, dirt, sand, stone…), capped at +10
 *
 * The full wrapped BlockState (including orientation) is stored so the BER can
 * reproduce the exact appearance of the original block.
 */
public class ShinbokuBlockEntity extends BlockEntity {

    public static final int CAPACITY        = 2000;
    public static final int SCAN_RANGE      = 8;
    public static final int SCAN_EVERY      = 200;
    public static final int DISTRIBUTE_EVERY = 20;

    private final SimpleReiStorage rei = new SimpleReiStorage(CAPACITY);

    @Nullable private BlockState wrappedState  = null;
    @Nullable private ResourceLocation wrappedBlockId = null;
    private int baseRate      = 2;
    private int naturalBonus  = 0;
    private int scanTimer      = SCAN_EVERY;
    private int distributeTimer = DISTRIBUTE_EVERY;

    public ShinbokuBlockEntity(BlockPos pos, BlockState state) {
        super(MusubiBlockEntities.SHINBOKU.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ShinbokuBlockEntity be) {
        be.scanTimer--;
        if (be.scanTimer <= 0) {
            be.scanTimer    = SCAN_EVERY;
            be.naturalBonus = scanNaturalRichness(level, pos);
        }

        be.rei.generate(be.baseRate + be.naturalBonus);

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
        wrappedState   = state;
        wrappedBlockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        baseRate       = computeBaseRate(state);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Nullable public BlockState getWrappedState()      { return wrappedState; }
    @Nullable public ResourceLocation getWrappedBlockId() { return wrappedBlockId; }

    private static int computeBaseRate(BlockState state) {
        if (state.is(BlockTags.CRYSTAL_SOUND_BLOCKS)) return 10;
        if (state.is(BlockTags.STONE_ORE_REPLACEABLES)) return 6;
        if (state.is(BlockTags.LOGS))                   return 4;
        return 2;
    }

    /** Drops the original block's item and the Shimenawa rope on removal. */
    public void dropContents(Level level, BlockPos pos) {
        if (wrappedBlockId != null) {
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(wrappedBlockId);
            block.ifPresent(b -> {
                Item item = b.asItem();
                if (item != Items.AIR) Block.popResource(level, pos, new ItemStack(item));
            });
        }
        Block.popResource(level, pos, new ItemStack(MusubiItems.SHIMENAWA.get()));
    }

    public IReiStorage getReiStorage()  { return rei; }
    public int getBaseRate()            { return baseRate; }
    public int getNaturalBonus()        { return naturalBonus; }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("rei", rei.getRaw());
        if (wrappedState != null) {
            tag.put("wrapped_state", NbtUtils.writeBlockState(wrappedState));
        }
        tag.putInt("natural_bonus", naturalBonus);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rei.setRaw(tag.getInt("rei"));
        if (tag.contains("wrapped_state")) {
            HolderGetter<Block> blockGetter = registries.lookupOrThrow(Registries.BLOCK);
            wrappedState   = NbtUtils.readBlockState(blockGetter, tag.getCompound("wrapped_state"));
            if (wrappedState.is(Blocks.AIR)) wrappedState = null;
            if (wrappedState != null) {
                wrappedBlockId = BuiltInRegistries.BLOCK.getKey(wrappedState.getBlock());
                baseRate       = computeBaseRate(wrappedState);
            }
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
