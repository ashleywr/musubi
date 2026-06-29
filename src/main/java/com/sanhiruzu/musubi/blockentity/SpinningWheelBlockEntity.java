package com.sanhiruzu.musubi.blockentity;

import com.sanhiruzu.musubi.block.SpinningWheelBlock;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
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
 * When the skein empties the block resets to SPINNING=false and the skein slot clears.
 */
public class SpinningWheelBlockEntity extends BlockEntity {

    private static final int TICKS_PER_EXTRACT = 20;

    private ItemStack skein = ItemStack.EMPTY;
    private int tickTimer = TICKS_PER_EXTRACT;

    public SpinningWheelBlockEntity(BlockPos pos, BlockState state) {
        super(MusubiBlockEntities.SPINNING_WHEEL.get(), pos, state);
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpinningWheelBlockEntity be) {
        if (be.skein.isEmpty()) return;

        be.tickTimer--;
        if (be.tickTimer > 0) return;
        be.tickTimer = TICKS_PER_EXTRACT;

        be.extractBatch(level, pos, state);
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

    // ── API used by SpinningWheelBlock ────────────────────────────────────────

    public ItemStack getSkein() {
        return skein;
    }

    public void loadSkein(ItemStack stack, Level level, BlockPos pos, BlockState state) {
        skein = stack;
        tickTimer = TICKS_PER_EXTRACT;
        level.setBlock(pos, state.setValue(SpinningWheelBlock.SPINNING, true), 3);
        setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }

    public ItemStack retrieveSkein(Level level, BlockPos pos, BlockState state) {
        ItemStack retrieved = skein;
        skein = ItemStack.EMPTY;
        level.setBlock(pos, state.setValue(SpinningWheelBlock.SPINNING, false), 3);
        setChanged();
        return retrieved;
    }

    public void dropSkein(Level level, BlockPos pos) {
        if (skein.isEmpty()) return;
        ItemEntity entity = new ItemEntity(
            level,
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            skein
        );
        level.addFreshEntity(entity);
        skein = ItemStack.EMPTY;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!skein.isEmpty()) {
            tag.put("skein", skein.save(registries));
        }
        tag.putInt("tick_timer", tickTimer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        skein = tag.contains("skein")
            ? ItemStack.parseOptional(registries, tag.getCompound("skein"))
            : ItemStack.EMPTY;
        tickTimer = tag.contains("tick_timer") ? tag.getInt("tick_timer") : TICKS_PER_EXTRACT;
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
