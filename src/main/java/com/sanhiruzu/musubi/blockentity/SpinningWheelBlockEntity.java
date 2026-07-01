package com.sanhiruzu.musubi.blockentity;

import com.sanhiruzu.musubi.block.SpinningWheelBlock;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.item.MusubiItems;
import com.sanhiruzu.musubi.rei.IReiStorage;
import com.sanhiruzu.musubi.rei.ReiUtil;
import com.sanhiruzu.musubi.rei.SimpleReiStorage;
import com.sanhiruzu.musubi.sound.MusubiSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Ticks down every TICKS_PER_EXTRACT server ticks, extracting one batch (up to 64)
 * from the loaded skein and ejecting it as an item entity out the block's front face.
 *
 * Also processes raw silk thread (1 per RAW_TICKS_PER_ITEM ticks) into magical silk thread.
 *
 * When the skein or raw input empties the block resets to SPINNING=false.
 */
public class SpinningWheelBlockEntity extends BlockEntity {

    private static final int TICKS_PER_EXTRACT  = 20;
    private static final int RAW_TICKS_PER_ITEM = 40;
    private static final int REI_CAPACITY       = 500;
    private static final int REI_PER_THREAD     = 5;
    private static final int DISTRIBUTE_EVERY   = 20;

    private ItemStack skein = ItemStack.EMPTY;
    private int tickTimer = TICKS_PER_EXTRACT;

    private ItemStack rawInput = ItemStack.EMPTY;
    private int rawTickTimer = RAW_TICKS_PER_ITEM;

    private final SimpleReiStorage rei = new SimpleReiStorage(REI_CAPACITY);
    private int distributeTimer = DISTRIBUTE_EVERY;

    public SpinningWheelBlockEntity(BlockPos pos, BlockState state) {
        super(MusubiBlockEntities.SPINNING_WHEEL.get(), pos, state);
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpinningWheelBlockEntity be) {
        if (!be.skein.isEmpty()) {
            be.tickTimer--;
            if (be.tickTimer <= 0) {
                be.tickTimer = TICKS_PER_EXTRACT;
                be.extractBatch(level, pos, state);
            }
        } else if (!be.rawInput.isEmpty()) {
            be.rawTickTimer--;
            if (be.rawTickTimer <= 0) {
                be.rawTickTimer = RAW_TICKS_PER_ITEM;
                be.processRawSilk(level, pos, state);
            }
        }

        if (--be.distributeTimer <= 0) {
            be.distributeTimer = DISTRIBUTE_EVERY;
            ReiUtil.distribute(be.rei, level, pos);
        }

        if (be.rei.isDirty()) {
            be.rei.clearDirty();
            be.setChanged();
        }
    }

    private void extractBatch(Level level, BlockPos pos, BlockState state) {
        SkeinContents contents = skein.get(MusubiDataComponents.SKEIN_CONTENTS);
        if (contents == null || contents.variants().isEmpty()) {
            clearSkein(level, pos, state);
            return;
        }

        SkeinContents.VariantEntry first = contents.variants().get(0);
        int amount = Math.min(64, first.count());

        Item item = BuiltInRegistries.ITEM.getOptional(first.itemId()).orElse(Items.AIR);
        if (item != Items.AIR) {
            ejectItem(level, pos, state, new ItemStack(item, amount));
        }

        List<SkeinContents.VariantEntry> newVariants = new ArrayList<>(contents.variants());
        int leftover = first.count() - amount;
        if (leftover <= 0) {
            newVariants.remove(0);
        } else {
            newVariants.set(0, new SkeinContents.VariantEntry(first.itemId(), leftover));
        }

        int newCount = contents.count() - amount;
        if (newVariants.isEmpty() || newCount <= 0) {
            clearSkein(level, pos, state);
            return;
        }

        SkeinContents updated = new SkeinContents(
            contents.needleTier(), contents.groupKey(), newCount,
            List.copyOf(newVariants), contents.displayColor(),
            contents.complexityScore(), contents.dataRef()
        );
        skein.set(MusubiDataComponents.SKEIN_CONTENTS, updated);
        setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
        level.playSound(null, pos, MusubiSounds.WHEEL_SPIN.get(), SoundSource.BLOCKS,
            0.6f, 0.9f + level.random.nextFloat() * 0.2f);
    }

    private void ejectItem(Level level, BlockPos pos, BlockState state, ItemStack stack) {
        Direction facing = state.getValue(SpinningWheelBlock.FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.65;
        double y = pos.getY() + 0.75;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.65;
        ItemEntity entity = new ItemEntity(level, x, y, z, stack);
        entity.setDeltaMovement(
            facing.getStepX() * 0.12,
            0.15,
            facing.getStepZ() * 0.12
        );
        level.addFreshEntity(entity);
    }

    private void clearSkein(Level level, BlockPos pos, BlockState state) {
        skein = ItemStack.EMPTY;
        level.setBlock(pos, state.setValue(SpinningWheelBlock.SPINNING, false), 3);
        setChanged();
    }

    private void processRawSilk(Level level, BlockPos pos, BlockState state) {
        rawInput.shrink(1);
        ejectItem(level, pos, state, new ItemStack(MusubiItems.MAGICAL_SILK_THREAD.get(), 1));
        rei.generate(REI_PER_THREAD);
        if (rawInput.isEmpty()) {
            rawInput = ItemStack.EMPTY;
            level.setBlock(pos, state.setValue(SpinningWheelBlock.SPINNING, false), 3);
        }
        setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
        level.playSound(null, pos, MusubiSounds.WHEEL_SPIN.get(), SoundSource.BLOCKS,
            0.6f, 1.1f + level.random.nextFloat() * 0.2f);
    }

    // ── API used by SpinningWheelBlock ────────────────────────────────────────

    public ItemStack getSkein() {
        return skein;
    }

    public ItemStack getRawInput() {
        return rawInput;
    }

    public void loadSkein(ItemStack stack, Level level, BlockPos pos, BlockState state) {
        skein = stack;
        tickTimer = TICKS_PER_EXTRACT;
        level.setBlock(pos, state.setValue(SpinningWheelBlock.SPINNING, true), 3);
        setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
        level.playSound(null, pos, MusubiSounds.WHEEL_LOAD.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
    }

    public ItemStack retrieveSkein(Level level, BlockPos pos, BlockState state) {
        ItemStack retrieved = skein;
        skein = ItemStack.EMPTY;
        level.setBlock(pos, state.setValue(SpinningWheelBlock.SPINNING, false), 3);
        setChanged();
        return retrieved;
    }

    public void loadRawInput(ItemStack stack, Level level, BlockPos pos, BlockState state) {
        if (rawInput.isEmpty()) {
            rawInput = stack.copy();
            stack.setCount(0);
        } else if (ItemStack.isSameItem(rawInput, stack)) {
            int space = rawInput.getMaxStackSize() - rawInput.getCount();
            int toAdd = Math.min(space, stack.getCount());
            if (toAdd > 0) {
                rawInput.grow(toAdd);
                stack.shrink(toAdd);
            }
        }
        if (!rawInput.isEmpty()) {
            rawTickTimer = RAW_TICKS_PER_ITEM;
            level.setBlock(pos, state.setValue(SpinningWheelBlock.SPINNING, true), 3);
            setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
            level.playSound(null, pos, MusubiSounds.WHEEL_LOAD.get(), SoundSource.BLOCKS, 0.9f, 1.0f);
        }
    }

    public ItemStack retrieveRawInput(Level level, BlockPos pos, BlockState state) {
        ItemStack retrieved = rawInput;
        rawInput = ItemStack.EMPTY;
        level.setBlock(pos, state.setValue(SpinningWheelBlock.SPINNING, false), 3);
        setChanged();
        return retrieved;
    }

    public IReiStorage getReiStorage() { return rei; }

    public void dropSkein(Level level, BlockPos pos) {
        if (!skein.isEmpty()) {
            level.addFreshEntity(new ItemEntity(
                level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, skein));
            skein = ItemStack.EMPTY;
        }
        if (!rawInput.isEmpty()) {
            level.addFreshEntity(new ItemEntity(
                level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, rawInput));
            rawInput = ItemStack.EMPTY;
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!skein.isEmpty()) {
            tag.put("skein", skein.save(registries));
        }
        tag.putInt("tick_timer", tickTimer);
        if (!rawInput.isEmpty()) {
            tag.put("raw_input", rawInput.save(registries));
        }
        tag.putInt("raw_tick_timer", rawTickTimer);
        tag.putInt("rei", rei.getRaw());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        skein = tag.contains("skein")
            ? ItemStack.parseOptional(registries, tag.getCompound("skein"))
            : ItemStack.EMPTY;
        tickTimer = tag.contains("tick_timer") ? tag.getInt("tick_timer") : TICKS_PER_EXTRACT;
        rawInput = tag.contains("raw_input")
            ? ItemStack.parseOptional(registries, tag.getCompound("raw_input"))
            : ItemStack.EMPTY;
        rawTickTimer = tag.contains("raw_tick_timer") ? tag.getInt("raw_tick_timer") : RAW_TICKS_PER_ITEM;
        rei.setRaw(tag.getInt("rei"));
    }

    // ── Sync ─────────────────────────────────────────────────────────────────

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
