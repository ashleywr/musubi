package com.sanhiruzu.musubi.item;

import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        boolean showDetails = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        if (contents == null) {
            MusubiTooltip.addLine(lines, ChatFormatting.DARK_GRAY, "musubi.skein.empty");
            if (showDetails) {
                lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.state"),
                    ChatFormatting.GOLD, Component.translatable("musubi.skein.empty_hint"),
                    ChatFormatting.GRAY));
            } else {
                MusubiTooltip.addShiftPrompt(lines);
            }
            return;
        }

        MusubiTooltip.addLine(lines,
            contents.isTagGrouped() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD,
            contents.isTagGrouped() ? "musubi.skein.kind_tag" : "musubi.skein.kind_item");

        if (showDetails) {
            lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.tooltip.target"),
                ChatFormatting.GOLD, groupComponent(contents),
                contents.isTagGrouped() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
            lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.skein.count"),
                ChatFormatting.GOLD, Component.literal(Integer.toString(contents.count())),
                ChatFormatting.AQUA));

            if (!contents.variants().isEmpty()) {
                lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.skein.variants"),
                    ChatFormatting.GOLD, Component.literal(Integer.toString(contents.variants().size())),
                    ChatFormatting.GRAY));
                contents.variants().forEach(v ->
                    MusubiTooltip.addBullet(lines, ChatFormatting.DARK_GRAY, ChatFormatting.GRAY,
                        Component.literal("").append(itemName(v.itemId()))
                            .append(Component.literal(" x " + v.count())))
                );
            }

            if (flag.isAdvanced()) {
                lines.add(MusubiTooltip.labelValue(Component.translatable("musubi.skein.complexity"),
                    ChatFormatting.GOLD, Component.literal(Integer.toString(contents.complexityScore())),
                    ChatFormatting.GRAY));
                contents.dataRef().ifPresent(id -> lines.add(MusubiTooltip.labelValue(
                    Component.translatable("musubi.skein.data_ref"), ChatFormatting.GOLD,
                    Component.literal(id.toString()), ChatFormatting.DARK_AQUA)));
            }
        } else {
            MusubiTooltip.addShiftPrompt(lines);
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

    private static Component groupComponent(SkeinContents contents) {
        ResourceLocation id = ResourceLocation.tryParse(contents.groupKey());
        if (id == null) return Component.literal(contents.groupKey());
        Component value = contents.isTagGrouped()
            ? Component.literal("#").append(itemName(id))
            : itemName(id);
        return value;
    }

    private static Component itemName(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id)
            .map(it -> (Component) Component.translatable(it.getDescriptionId()))
            .orElse(Component.literal(id.toString()));
    }
}
