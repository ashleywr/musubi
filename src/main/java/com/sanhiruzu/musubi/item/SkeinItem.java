package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import net.minecraft.network.chat.Component;
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

    // TODO: custom merge/stacking behavior (SkeinContents.mergesWith() as the predicate)
    // TODO: tinted item model using displayColor (custom ItemColor registration on client)
    // TODO: right-click-to-deposit shortcut (shift-right-click spool rack with skein in hand)
}
