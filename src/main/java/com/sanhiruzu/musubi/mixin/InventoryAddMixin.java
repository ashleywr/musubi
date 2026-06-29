package com.sanhiruzu.musubi.mixin;

import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.item.SkeinItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Intercepts Inventory.add(ItemStack) so that incoming Silk Skeins merge their
 * SkeinContents into an existing compatible skein instead of occupying a new slot.
 * Two skeins are compatible when SkeinContents.mergesWith() returns true (same
 * needleTier and groupKey).
 */
@Mixin(Inventory.class)
public class InventoryAddMixin {

    @Shadow public List<ItemStack> items;

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true)
    private void musubi$mergeSkeinOnAdd(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!(stack.getItem() instanceof SkeinItem)) return;

        SkeinContents incoming = stack.get(MusubiDataComponents.SKEIN_CONTENTS);
        if (incoming == null) return;

        for (ItemStack existing : items) {
            if (!(existing.getItem() instanceof SkeinItem)) continue;
            SkeinContents existingContents = existing.get(MusubiDataComponents.SKEIN_CONTENTS);
            if (existingContents == null || !existingContents.mergesWith(incoming)) continue;

            existing.set(MusubiDataComponents.SKEIN_CONTENTS, existingContents.mergeWith(incoming));
            stack.setCount(0);
            cir.setReturnValue(true);
            return;
        }
    }
}
