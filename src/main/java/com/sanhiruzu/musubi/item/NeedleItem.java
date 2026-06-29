package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.component.MusubiDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The needle — lives in a Curios slot, never occupies inventory.
 *
 * tier controls how group_key is computed at sewing time:
 *   0 = bone    → broadest matching tag  (e.g. #minecraft:logs)
 *   1 = iron    → exact item ID          (e.g. minecraft:oak_log)
 *   2 = silver  → cross-tag blend        (TBD — mid-game gate)
 *   3 = orihime → anything               (Eternal Starlight gate)
 *
 * Sewing uses a two-phase hold mechanic:
 *   Phase 1 (ticks 0–29): startup animation, free cancel at any point.
 *   Phase 2 (tick 30+):   drain begins; one charge consumed at phase entry.
 *
 * maxCharges is the starting and recharge-cap value. Current charges live in the
 * NEEDLE_CHARGES DataComponent so they persist across sessions and inventory moves.
 */
public class NeedleItem extends Item {

    /** Ticks of startup before drain begins (1.5 s at 20 t/s). */
    public static final int STARTUP_TICKS = 30;

    public final int tier;
    public final int maxCharges;

    public NeedleItem(int tier, int maxCharges, Properties properties) {
        super(properties);
        this.tier = tier;
        this.maxCharges = maxCharges;
    }

    // ── Charge helpers ────────────────────────────────────────────────────────

    public int getCharges(ItemStack stack) {
        return stack.getOrDefault(MusubiDataComponents.NEEDLE_CHARGES, maxCharges);
    }

    public void setCharges(ItemStack stack, int charges) {
        stack.set(MusubiDataComponents.NEEDLE_CHARGES, Math.max(0, Math.min(charges, maxCharges)));
    }

    public void consumeCharge(ItemStack stack) {
        setCharges(stack, getCharges(stack) - 1);
    }

    public boolean hasCharges(ItemStack stack) {
        return getCharges(stack) > 0;
    }

    // ── Use mechanic ──────────────────────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!hasCharges(stack)) {
            player.displayClientMessage(Component.translatable("musubi.needle.no_charges"), true);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // BLOCK gives a two-handed concentration pose appropriate for fine work.
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        // Arbitrarily long; the mechanic is release-driven, not duration-driven.
        return 72000;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (level.isClientSide) return;
        int ticksHeld = getUseDuration(stack, entity) - remainingUseTicks;
        if (ticksHeld == STARTUP_TICKS) {
            // Phase 1 complete — Phase 2 drain begins on next tick.
            // TODO: consume one charge here; begin draining target items into SkeinContents.
        }
        // TODO: Phase 2 per-tick drain logic.
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        int ticksHeld = getUseDuration(stack, entity) - timeCharged;
        if (ticksHeld < STARTUP_TICKS) {
            // Released during Phase 1 — free cancel; charges untouched, no skein created.
            return;
        }
        // Released during Phase 2 — TODO: finalize the in-progress skein from partial drain.
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        int charges = getCharges(stack);
        if (charges > 0) {
            lines.add(Component.translatable("musubi.needle.charges", charges));
        } else {
            lines.add(Component.translatable("musubi.needle.no_charges"));
        }
    }

    // TODO: Curios slot registration (ICapabilityProvider via modEventBus)
    // TODO: group_key computation by tier (tag lookup vs item ID)
    // TODO: complexity scoring for drain rate and skein capacity budget
}
