package com.sanhiruzu.musubi;

import com.mojang.blaze3d.platform.NativeImage;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.item.MusubiItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class MusubiClient {

    /** Dominant-color cache so we only sample a sprite once per item type. */
    private static final Map<ResourceLocation, Integer> ITEM_COLOR_CACHE = new HashMap<>();

    public static void init(FMLClientSetupEvent event) {
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // Silk Skein —
        //   layer0: solid color from the first item variant's dominant texture color (white if empty)
        //   layer1: always a slowly cycling rainbow shimmer
        event.register((stack, tintIndex) -> {
            if (tintIndex == 0) {
                SkeinContents contents = stack.get(MusubiDataComponents.SKEIN_CONTENTS);
                if (contents == null || contents.count() == 0 || contents.variants().isEmpty()) return -1;
                return getDominantColor(contents.variants().get(0).itemId()) | 0xFF000000;
            }
            if (tintIndex == 1) return rainbowColor();
            return -1;
        }, MusubiItems.SKEIN.get());

        // Silk Thread — no item-based color, only the rainbow shimmer on layer1.
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1) return rainbowColor();
            return -1;
        }, MusubiItems.SILK_THREAD.get());
    }

    // ── Rainbow ───────────────────────────────────────────────────────────────

    private static int rainbowColor() {
        Minecraft mc = Minecraft.getInstance();
        long tick = (mc.level != null) ? mc.level.getGameTime() : System.currentTimeMillis() / 50L;
        float hue = (tick % 200L) / 200.0f;  // full cycle every 200 ticks (10 s)
        return java.awt.Color.HSBtoRGB(hue, 0.65f, 1.0f) | 0xFF000000;
    }

    // ── Dominant color from item texture ──────────────────────────────────────

    private static int getDominantColor(ResourceLocation itemId) {
        return ITEM_COLOR_CACHE.computeIfAbsent(itemId, MusubiClient::computeDominantColor);
    }

    private static int computeDominantColor(ResourceLocation itemId) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == null || item == Items.AIR) return 0xC8A864;

            BakedModel model = mc.getItemRenderer().getModel(new ItemStack(item), null, null, 0);
            TextureAtlasSprite sprite = model.getParticleIcon(ModelData.EMPTY);
            SpriteContents contents = sprite.contents();

            // SpriteContents keeps the source NativeImage between resource reloads.
            // Access is via reflection since there is no public pixel API.
            Field imgField = SpriteContents.class.getDeclaredField("originalImage");
            imgField.setAccessible(true);
            NativeImage image = (NativeImage) imgField.get(contents);
            if (image == null) return 0xC8A864;

            return sampleDominantColor(image, contents.width(), contents.height());
        } catch (Exception e) {
            return 0xC8A864; // warm tan fallback for any failure
        }
    }

    /**
     * Weighted-average pixel color, skipping near-grays and fully-transparent pixels.
     * Weight = saturation², so vivid pixels dominate over muted ones.
     *
     * NativeImage.getPixelRGBA returns ABGR (alpha=MSB, red=LSB) in little-endian.
     */
    private static int sampleDominantColor(NativeImage image, int w, int h) {
        double rSum = 0, gSum = 0, bSum = 0, totalWeight = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = image.getPixelRGBA(x, y);
                int a = (abgr >> 24) & 0xFF;
                if (a < 128) continue;
                int r = abgr & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
                float sat = hsb[1];
                if (sat < 0.15f) continue; // ignore near-grays and whites
                double w2 = sat * sat;
                rSum += r * w2;
                gSum += g * w2;
                bSum += b * w2;
                totalWeight += w2;
            }
        }
        if (totalWeight < 0.01) return 0xC8A864; // very grey item → warm tan
        return ((int)(rSum / totalWeight) << 16)
             | ((int)(gSum / totalWeight) << 8)
             |  (int)(bSum / totalWeight);
    }
}
