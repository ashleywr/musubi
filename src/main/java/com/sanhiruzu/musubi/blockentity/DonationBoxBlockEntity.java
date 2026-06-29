package com.sanhiruzu.musubi.blockentity;

import com.sanhiruzu.musubi.rei.IReiStorage;
import com.sanhiruzu.musubi.rei.ReiUtil;
import com.sanhiruzu.musubi.rei.SimpleReiStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Saisen-bako (賽銭箱) — a donation/offering box.
 *
 * Ritual flow:
 *   1. Right-click with any item  → donate it (item consumed, REI value queued).
 *   2. Right-click with empty hand → first bow (message shown, 5-second window opens).
 *   3. Right-click empty hand again  → second bow → ritual completes:
 *        pending REI is generated, clapping + bell sounds play, golden sparkles burst.
 *
 * Donating items without completing the ritual loses them — the offering has been made;
 * the gods don't give back.
 *
 * REI per item is based on its rarity:
 *   COMMON 10 · UNCOMMON 30 · RARE 100 · EPIC 500
 */
public class DonationBoxBlockEntity extends BlockEntity {

    public static final int BOW_WINDOW    = 100; // ticks (5 s) to complete the second bow
    public static final int CAPACITY      = 5000;
    public static final int DISTRIBUTE_EVERY = 20;

    private final SimpleReiStorage rei = new SimpleReiStorage(CAPACITY);
    private int pendingREI   = 0;
    private int bowCount     = 0;
    private int bowTimer     = 0;
    private int distributeTimer = DISTRIBUTE_EVERY;

    public DonationBoxBlockEntity(BlockPos pos, BlockState state) {
        super(MusubiBlockEntities.DONATION_BOX.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
            DonationBoxBlockEntity be) {
        if (be.bowTimer > 0) be.bowTimer--;

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

    // ── Interaction ──────────────────────────────────────────────────────────

    /** Called when the player right-clicks with an item in hand. */
    public void donate(ItemStack stack, Player player) {
        if (stack.isEmpty()) return;
        int value = reiValue(stack.getItem());
        pendingREI += value;
        stack.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.BLOCKS, 0.5f, 0.9f);
        player.displayClientMessage(
            Component.translatable("musubi.donation_box.offered", value),
            true);
        setChanged();
    }

    /**
     * Called when the player right-clicks with an empty hand.
     * Returns the result so the block can play the right sounds.
     */
    public BowResult bow(Player player) {
        if (bowCount == 0 || bowTimer == 0) {
            // Start the ritual window.
            bowCount = 1;
            bowTimer = BOW_WINDOW;
            player.displayClientMessage(
                Component.translatable("musubi.donation_box.bow_first"),
                true);
            return BowResult.FIRST_BOW;
        } else {
            // Second bow — complete the ritual.
            bowCount = 0;
            bowTimer = 0;
            if (pendingREI > 0) {
                rei.generate(pendingREI);
                pendingREI = 0;
                setChanged();
            }
            player.displayClientMessage(
                Component.translatable("musubi.donation_box.ritual_complete"),
                true);
            return BowResult.RITUAL_COMPLETE;
        }
    }

    public int getPendingREI() { return pendingREI; }
    public IReiStorage getReiStorage() { return rei; }
    public int getBowCount() { return bowCount; }

    // ── REI valuation ─────────────────────────────────────────────────────────

    private static int reiValue(Item item) {
        return switch (item.getDefaultInstance().getRarity()) {
            case EPIC     -> 500;
            case RARE     -> 100;
            case UNCOMMON -> 30;
            default       -> 10; // COMMON
        };
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("rei",         rei.getRaw());
        tag.putInt("pending_rei", pendingREI);
        tag.putInt("bow_count",   bowCount);
        tag.putInt("bow_timer",   bowTimer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rei.setRaw(tag.getInt("rei"));
        pendingREI = tag.getInt("pending_rei");
        bowCount   = tag.getInt("bow_count");
        bowTimer   = tag.getInt("bow_timer");
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

    public enum BowResult { FIRST_BOW, RITUAL_COMPLETE }
}
