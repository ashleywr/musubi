package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.component.MusubiDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

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
 * maxCharges is the starting and recharge-cap value. Current charges live in the
 * NEEDLE_CHARGES DataComponent so they persist across sessions and inventory moves.
 */
public class NeedleItem extends Item {

    public final int tier;
    public final int maxCharges;

    public NeedleItem(int tier, int maxCharges, Properties properties) {
        super(properties);
        this.tier = tier;
        this.maxCharges = maxCharges;
    }

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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("musubi.needle.charges", getCharges(stack)));
    }

    // TODO: Phase 1 startup animation (hold right-click ~1.5s, free cancel, no charge spent)
    // TODO: Phase 2 drain (stack depletes into SkeinContents, release early to stop)
    // TODO: Curios slot registration (ICapabilityProvider via modEventBus)
    // TODO: group_key computation by tier (tag lookup vs item ID)
    // TODO: complexity scoring for drain rate and skein capacity budget
}
