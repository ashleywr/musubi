package com.sanhiruzu.musubi.mixin;

import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.item.SkeinItem;
import com.sanhiruzu.musubi.item.TamaItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Hooks Inventory.add(ItemStack) for two Musubi behaviours:
 *
 * <ol>
 *   <li><b>Bind-on-pickup</b> — if a non-skein item is picked up while the player holds a Tama
 *       whose filter accepts that type, it is bound into the Tama instead of taking a slot. Any
 *       remainder that doesn't fit (Tama full) falls through to the normal add.</li>
 *   <li><b>Skein merge</b> — an incoming Silk Skein merges its contents into an existing
 *       compatible skein (same needleTier + groupKey) rather than occupying a new slot.</li>
 * </ol>
 */
@Mixin(Inventory.class)
public class InventoryAddMixin {

    @Shadow public List<ItemStack> items;
    @Shadow public Player player;

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true)
    private void musubi$onAdd(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty()) return;

        // (2) Skein merge.
        if (stack.getItem() instanceof SkeinItem) {
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
            return;
        }

        // (1) Bind-on-pickup — into any Tama in the inventory, or one worn in a curio slot.
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return;
        for (ItemStack inv : items) {
            if (!(inv.getItem() instanceof TamaItem)) continue;
            if (!TamaItem.accepts(inv, id)) continue;
            int deposited = TamaItem.depositInto(inv, id, stack.getCount());
            if (deposited > 0) {
                stack.shrink(deposited);
                TamaItem.playBindEffect(player, id);
                if (stack.isEmpty()) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        // Worn Tama (belt curio) — so it gathers without occupying the hand or a slot.
        ItemStack worn = com.sanhiruzu.musubi.curio.MusubiCurios.findWornTama(player);
        if (worn != null && TamaItem.accepts(worn, id)) {
            int deposited = TamaItem.depositInto(worn, id, stack.getCount());
            if (deposited > 0) {
                stack.shrink(deposited);
                TamaItem.playBindEffect(player, id);
                if (stack.isEmpty()) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
}
