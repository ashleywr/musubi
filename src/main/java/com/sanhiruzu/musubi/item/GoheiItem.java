package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.blockentity.ShrineStoneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Gohei (御幣) — a ritual wand with folded paper streamers used in Shinto
 * purification rites (harae). Waving it near a Shrine Stone consecrates the
 * area, doubling REI generation for 5 minutes.
 *
 * Has 20 uses before the streamers are spent.
 */
public class GoheiItem extends Item {

    public static final int BLESS_RANGE = 8;
    public static final int BLESS_TICKS = 6000; // 5 minutes

    public GoheiItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) return InteractionResultHolder.success(stack);

        BlockPos origin = player.blockPosition();
        int count = 0;

        for (BlockPos p : BlockPos.betweenClosed(
                origin.offset(-BLESS_RANGE, -BLESS_RANGE, -BLESS_RANGE),
                origin.offset( BLESS_RANGE,  BLESS_RANGE,  BLESS_RANGE))) {
            if (level.getBlockEntity(p) instanceof ShrineStoneBlockEntity shrine) {
                shrine.bless(BLESS_TICKS);
                count++;
            }
        }

        if (count > 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.9f, 1.3f);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1.2, player.getZ(),
                    24, 0.5, 0.5, 0.5, 0.05);
            }
            stack.hurtAndBreak(1, (LivingEntity) player, LivingEntity.getSlotForHand(hand));
            player.getCooldowns().addCooldown(this, 40); // 2-second cooldown between waves
        }

        return InteractionResultHolder.success(stack);
    }
}
