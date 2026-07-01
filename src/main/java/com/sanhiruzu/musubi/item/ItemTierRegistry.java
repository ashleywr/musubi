package com.sanhiruzu.musubi.item;

import net.minecraft.resources.ResourceLocation;

/**
 * Classifies items into silk tiers (0=Feather, 1=Crinkle, 2=Rinzu) based on rarity and value.
 *
 * Tier classification:
 * - Tier 0 (Feather): common items, basic materials, wood, stone, dirt
 * - Tier 1 (Crinkle): intermediate items, iron, copper, tools, processed materials
 * - Tier 2 (Rinzu): rare items, diamonds, emeralds, gold, enchanted books, special items
 */
public class ItemTierRegistry {

    public static int getTierForItem(ResourceLocation itemId) {
        if (itemId == null) return 1;

        String namespace = itemId.getNamespace();
        String path = itemId.getPath();

        // Tier 2 (Rinzu): rare/precious items
        if (isTier2Item(namespace, path)) {
            return 2;
        }

        // Tier 1 (Crinkle): intermediate items
        if (isTier1Item(namespace, path)) {
            return 1;
        }

        // Tier 0 (Feather): common items (default)
        return 0;
    }

    private static boolean isTier2Item(String namespace, String path) {
        // Precious gems and metals
        if (path.contains("diamond") || path.contains("emerald") || path.contains("gold") ||
            path.contains("netherite") || path.contains("amethyst") || path.contains("copper_block")) {
            return true;
        }

        // Rare/special modded items
        if (path.contains("ruby") || path.contains("sapphire") || path.contains("topaz") ||
            path.contains("pearl") || path.contains("gem") || path.contains("crystal")) {
            return true;
        }

        // Enchanted books and special items
        if (path.contains("enchanted_book") || path.contains("mending") || path.contains("dragon_egg") ||
            path.contains("beacon") || path.contains("conduit")) {
            return true;
        }

        // Combat armor and high-tier tools
        if (path.contains("netherite_") || path.contains("diamond_armor") ||
            path.contains("diamond_helmet") || path.contains("diamond_chestplate")) {
            return true;
        }

        // End/Nether special items
        if (path.contains("end_rod") || path.contains("purpur") || path.contains("sculk")) {
            return true;
        }

        return false;
    }

    private static boolean isTier1Item(String namespace, String path) {
        // Iron and copper items
        if (path.contains("iron_") || path.contains("copper_")) {
            return true;
        }

        // Processed materials
        if (path.contains("redstone") || path.contains("lapis") || path.contains("quartz") ||
            path.contains("glowstone") || path.contains("prismarine")) {
            return true;
        }

        // Tools and equipment (non-diamond/netherite)
        if (path.contains("_sword") || path.contains("_pickaxe") || path.contains("_axe") ||
            path.contains("_shovel") || path.contains("_hoe") || path.contains("_helmet") ||
            path.contains("_chestplate") || path.contains("_leggings") || path.contains("_boots")) {
            // Exclude low-tier (wood, stone) and high-tier (diamond, netherite) tools
            if (!path.contains("wooden_") && !path.contains("stone_") && !path.contains("diamond_") &&
                !path.contains("netherite_") && !path.contains("golden_")) {
                return true;
            }
        }

        // Dyes and pigments
        if (path.contains("dye") || path.contains("pigment")) {
            return true;
        }

        // Functional blocks
        if (path.contains("repeater") || path.contains("comparator") || path.contains("piston") ||
            path.contains("observer") || path.contains("dispenser") || path.contains("dropper")) {
            return true;
        }

        // Potions and brewing
        if (path.contains("potion") || path.contains("brewing_stand") || path.contains("cauldron")) {
            return true;
        }

        return false;
    }
}
