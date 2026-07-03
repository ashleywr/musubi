package com.sanhiruzu.musubi.rite;

import com.sanhiruzu.musubi.item.MusubiItems;
import com.sanhiruzu.musubi.network.MoonRiteVfxPayload;
import com.sanhiruzu.musubi.util.MoonReflectionMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The moon rite: throw a feather into the moon's reflection on still water.
 *
 * <p>This is the server-side companion to the water moon reflection shader. The
 * shader draws the specular path each player sees on their own screen; when a thrown
 * feather splashes down, this handler recomputes the same optics from the
 * <em>thrower's</em> eye via {@link MoonReflectionMath}, so the rite succeeds exactly
 * where the thrower saw the shimmer. The reflection is viewer-dependent — two players
 * throwing at the same pond have different valid zones, each matching their own view.</p>
 *
 * <p>On success one feather is consumed from the floating stack and a bone tama rises
 * out of the water at the splash point.</p>
 */
public final class MoonRite {

    /** Persistent-data flag: each thrown item gets exactly one splash-down evaluation. */
    private static final String CHECKED_TAG = "musubi:moon_rite_checked";
    /** Only fresh throws count — an item left bobbing since last night can't re-trigger. */
    private static final int MAX_ITEM_AGE_TICKS = 200;
    /** How far above the item to search for the water surface it just broke. */
    private static final int SURFACE_SEARCH_UP = 4;

    private MoonRite() {}

    public static void onEntityTick(EntityTickEvent.Post event) {
        // Cheap filters first: this listener runs for every entity tick.
        if (!(event.getEntity() instanceof ItemEntity item)) return;
        if (item.level().isClientSide || !item.isInWater()) return;
        if (item.tickCount > MAX_ITEM_AGE_TICKS) return;
        if (!item.getItem().is(Items.FEATHER)) return;
        if (item.getPersistentData().getBoolean(CHECKED_TAG)) return;
        item.getPersistentData().putBoolean(CHECKED_TAG, true);

        if (!(item.level() instanceof ServerLevel level)) return;
        if (!(item.getOwner() instanceof Player thrower)) return; // must be player-thrown
        if (!MoonReflectionMath.isMoonlitNight(level)) return;

        BlockPos surfacePos = findSurface(level, item.blockPosition());
        if (surfacePos == null) return;

        Vec3 splash = new Vec3(item.getX(),
            MoonReflectionMath.surfaceY(level, surfacePos), item.getZ());
        if (!MoonReflectionMath.isInReflection(level, thrower.getEyePosition(), splash)) return;

        completeRite(level, item, thrower, surfacePos, splash);
    }

    /**
     * The item may have sunk a block or two below the surface by the time we see it in
     * water; walk up to the topmost water block and require it to be reflective.
     */
    private static BlockPos findSurface(ServerLevel level, BlockPos start) {
        BlockPos.MutableBlockPos m = start.mutable();
        for (int i = 0; i < SURFACE_SEARCH_UP && !level.getFluidState(m.above()).isEmpty(); i++) {
            m.move(0, 1, 0);
        }
        return MoonReflectionMath.isReflectiveWater(level, m) ? m.immutable() : null;
    }

    private static void completeRite(ServerLevel level, ItemEntity item, Player thrower,
                                     BlockPos pos, Vec3 splash) {
        ItemStack stack = item.getItem().copy();
        stack.shrink(1);
        if (stack.isEmpty()) {
            item.discard();
        } else {
            // One rite per throw: the remainder keeps its checked flag and floats
            // inert until someone fishes it out and throws again.
            item.setItem(stack);
        }

        // The moon's answer rises out of the water where the feather sank.
        ItemEntity reward = new ItemEntity(level, splash.x, splash.y + 0.1, splash.z,
            new ItemStack(MusubiItems.BONE_TAMA.get()));
        reward.setDeltaMovement(0.0, 0.28, 0.0);
        reward.setDefaultPickUpDelay();
        level.addFreshEntity(reward);

        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.BLOCKS, 0.8F, 1.35F);
        PacketDistributor.sendToAllPlayers(MoonRiteVfxPayload.create(pos, level.getGameTime(), 48));
        level.sendParticles(ParticleTypes.END_ROD,
            splash.x, splash.y + 0.15, splash.z, 10, 0.18, 0.08, 0.18, 0.01);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT,
            splash.x, splash.y + 0.05, splash.z, 12, 0.22, 0.08, 0.22, 0.08);
        thrower.displayClientMessage(Component.translatable("musubi.moon_ritual.complete"), true);
    }
}
