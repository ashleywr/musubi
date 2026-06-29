package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The skein — portable compressed thread carrying item data.
 *
 * Skein stacking is non-standard: two skeins merge when their SkeinContents passes
 * SkeinContents.mergesWith(), not when their DataComponents are byte-equal (which they
 * never would be since counts differ). This requires a custom IItemStackExtension or
 * container event hook — see TODO below.
 *
 * Display color is stored in SkeinContents.displayColor as packed RGB (0xRRGGBB).
 * The client reads it for spool rack rendering; no texture sampling ever occurs at runtime.
 */
public class SkeinItem extends Item {

    public SkeinItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        SkeinContents contents = stack.get(MusubiDataComponents.SKEIN_CONTENTS);
        if (contents == null) {
            lines.add(Component.translatable("musubi.skein.empty"));
            return;
        }

        if (contents.isTagGrouped()) {
            lines.add(Component.translatable("musubi.skein.contents_tag",
                contents.groupKey(), contents.count()));
        } else {
            lines.add(Component.translatable("musubi.skein.contents_item",
                contents.groupKey(), contents.count()));
        }

        if (flag.isAdvanced() && !contents.variants().isEmpty()) {
            contents.variants().forEach(v ->
                lines.add(Component.literal("  " + v.itemId() + " × " + v.count()))
            );
        }
    }

    // Right-click a held skein onto a slot-skein in inventory GUI → merge held into slot.
    @Override
    public boolean overrideStackedOnOther(ItemStack held, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        ItemStack target = slot.getItem();
        if (!(target.getItem() instanceof SkeinItem)) return false;
        SkeinContents heldContents   = held.get(MusubiDataComponents.SKEIN_CONTENTS);
        SkeinContents targetContents = target.get(MusubiDataComponents.SKEIN_CONTENTS);
        if (heldContents == null || targetContents == null) return false;
        if (!targetContents.mergesWith(heldContents)) return false;
        target.set(MusubiDataComponents.SKEIN_CONTENTS, targetContents.mergeWith(heldContents));
        held.shrink(1);
        return true;
    }

    // Right-click something onto a slot-skein in inventory GUI → merge if compatible.
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack slotSkein, ItemStack held, Slot slot,
            ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY) return false;
        if (!(held.getItem() instanceof SkeinItem)) return false;
        SkeinContents slotContents = slotSkein.get(MusubiDataComponents.SKEIN_CONTENTS);
        SkeinContents heldContents = held.get(MusubiDataComponents.SKEIN_CONTENTS);
        if (slotContents == null || heldContents == null) return false;
        if (!slotContents.mergesWith(heldContents)) return false;
        slotSkein.set(MusubiDataComponents.SKEIN_CONTENTS, slotContents.mergeWith(heldContents));
        held.shrink(1);
        return true;
    }

    // TODO: right-click-to-deposit shortcut (shift-right-click spool rack with skein in hand)
}
