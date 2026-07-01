package com.sanhiruzu.musubi;

import com.mojang.blaze3d.platform.NativeImage;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.curio.MusubiCurios;
import com.sanhiruzu.musubi.item.MusubiItems;
import com.sanhiruzu.musubi.client.render.TamaCollectionVfx;
import com.sanhiruzu.musubi.client.render.ItomakiUnspoolVfx;
import com.sanhiruzu.musubi.client.render.MoonReflectionVfx;
import com.sanhiruzu.musubi.network.TamaBindVfxPayload;
import com.sanhiruzu.musubi.network.ItomakiUnspoolVfxPayload;
import com.sanhiruzu.musubi.network.MoonRiteVfxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.sanhiruzu.musubi.blockentity.MusubiBlockEntities;
import com.sanhiruzu.musubi.client.render.ItomakiBER;
import com.sanhiruzu.musubi.client.render.MarudaiBER;
import com.sanhiruzu.musubi.client.render.TamaCurioRenderer;
import com.sanhiruzu.musubi.client.render.ShinbokuBER;
import com.sanhiruzu.musubi.client.render.SpinningWheelBER;
import com.sanhiruzu.musubi.client.render.SpoolRackBER;
import com.sanhiruzu.musubi.client.render.TamaSpin;
import com.sanhiruzu.musubi.item.TamaItem;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class MusubiClient {

    /** Dominant-color cache so we only sample a sprite once per item type. */
    private static final Map<ResourceLocation, Integer> ITEM_COLOR_CACHE = new HashMap<>();
    /** Item palette cache so strand VFX can sample once at spawn and reuse the result. */
    private static final Map<ResourceLocation, ItemColorPalette> ITEM_PALETTE_CACHE = new HashMap<>();

    public static void init(FMLClientSetupEvent event) {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            com.sanhiruzu.musubi.client.MusubiKeybinds::onClientTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(MusubiClient::onRenderGui);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(MusubiClient::onItemTooltip);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(TamaCollectionVfx::onRenderLevelStage);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ItomakiUnspoolVfx::onRenderLevelStage);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(MoonReflectionVfx::onRenderLevelStage);

        event.enqueueWork(() -> {
            registerTamaStrandProperty();
            registerCuriosRenderers();
        });
    }

    /** Advance TamaSpin once per frame, before any item rendering occurs. */
    private static void onRenderGui(RenderGuiEvent.Pre event) {
        TamaSpin.INSTANCE.beginFrame();
    }

    /** Mark TamaSpin as hovered whenever a tama's tooltip is being built (i.e. it is hovered). */
    private static void onItemTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().getItem() instanceof TamaItem) {
            TamaSpin.INSTANCE.markHoveredThisFrame();
        }
    }

    /** Drives the {@code musubi:strands} model property = how full the Tama is, in 0–4 steps
     *  (0 = empty/bare bobbin, 4 = capacity), selecting how much silk the staged models wrap on.
     *  Linear: each strand layer covers a quarter of capacity, so it reads like a fuel gauge. */
    private static void registerTamaStrandProperty() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, "strands");
        net.minecraft.client.renderer.item.ClampedItemPropertyFunction fn =
            (stack, level, entity, seed) -> {
                if (!(stack.getItem() instanceof TamaItem tama)) return 0f;
                int total = stack.getOrDefault(MusubiDataComponents.TAMA_STORAGE,
                    com.sanhiruzu.musubi.component.TamaStorage.EMPTY).total();
                if (total <= 0) return 0f;
                float fill = Math.min(1f, total / (float) Math.max(1, tama.storageCapacity(stack)));
                int stage = (int) Math.ceil(fill * 4f);
                return Math.min(4, Math.max(1, stage));
            };
        for (var holder : java.util.List.of(
                MusubiItems.BONE_TAMA, MusubiItems.IRON_TAMA,
                MusubiItems.SILVER_TAMA, MusubiItems.ORIHIME_TAMA)) {
            net.minecraft.client.renderer.item.ItemProperties.register(holder.get(), id, fn);
        }
    }

    /** Registers the belt renderers for all tama tiers. */
    private static void registerCuriosRenderers() {
        TamaCurioRenderer renderer = new TamaCurioRenderer();
        CuriosRendererRegistry.register(MusubiItems.BONE_TAMA.get(), () -> renderer);
        CuriosRendererRegistry.register(MusubiItems.IRON_TAMA.get(), () -> renderer);
        CuriosRendererRegistry.register(MusubiItems.SILVER_TAMA.get(), () -> renderer);
        CuriosRendererRegistry.register(MusubiItems.ORIHIME_TAMA.get(), () -> renderer);
    }

    /**
     * Client-side bind/collection VFX: render a strand from the source to the target Tama/player.
     */
    public static void playTamaBindVfx(net.minecraft.world.level.Level level, TamaBindVfxPayload payload) {
        if (!(level instanceof ClientLevel clientLevel)) return;
        TamaCollectionVfx.accept(clientLevel, payload);
    }

    /**
     * Client-side unspool VFX: render threads flowing from an Itomaki to nearby Spool Racks.
     */
    public static void playItomakiUnspool(net.minecraft.world.level.Level level, ItomakiUnspoolVfxPayload payload) {
        if (!(level instanceof ClientLevel)) return;
        ItomakiUnspoolVfx.accept(payload);
    }

    /** Client-side moon rite effect: render the response over the ritual water source. */
    public static void playMoonRiteVfx(net.minecraft.world.level.Level level, MoonRiteVfxPayload payload) {
        if (!(level instanceof ClientLevel clientLevel)) return;
        MoonReflectionVfx.accept(clientLevel, payload);
    }

    /**
     * Legacy client-side wrap effect helper retained for compatibility with older call sites.
     */
    public static void playTamaBindVfx(net.minecraft.world.level.Level level, int entityId, ResourceLocation itemId) {
        if (!(level instanceof ClientLevel clientLevel)) return;
        Entity entity = clientLevel.getEntity(entityId);
        if (!(entity instanceof Player player)) return;
        spawnTamaBindVfx(clientLevel, player, getDominantColor(itemId));
    }

    private static void spawnTamaBindVfx(ClientLevel level, Player wearer, int color) {
        var anchor = MusubiCurios.beltAnchor(wearer);
        int wrapColor = blendColor(color, 0xF8F1E0, 0.82f);
        int cream = 0xF8F1E0;

        level.addParticle(new DustParticleOptions(unpackRgb(color), 0.95f),
            anchor.x, anchor.y + 0.03, anchor.z, 0.02, 0.03, 0.02);
        level.addParticle(ParticleTypes.SMOKE,
            anchor.x, anchor.y + 0.02, anchor.z, 0.01, 0.01, 0.01);

        double phase = level.getGameTime() * 0.55 + wearer.getId() * 0.31;
        for (int i = 0; i < 16; i++) {
            double t = i / 15.0;
            double angle = phase + t * Math.PI * 7.0;
            double radius = 0.18 * (1.0 - t) + 0.02;
            double x = anchor.x + Math.cos(angle) * radius;
            double y = anchor.y + 0.02 + t * 0.18;
            double z = anchor.z + Math.sin(angle) * radius;

            int stepColor = blendColor(wrapColor, cream, (float) Math.pow(t, 1.05));
            level.addParticle(new DustParticleOptions(unpackRgb(stepColor), 0.78f),
                x, y, z, -0.01, 0.01, -0.01);
            if (i % 3 == 0) {
                level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, 0.01, 0.0);
            }
        }

        for (int i = 0; i < 6; i++) {
            double angle = phase + i * (Math.PI * 2.0 / 6.0);
            double radius = 0.045 + (i % 2) * 0.02;
            double x = anchor.x + Math.cos(angle) * radius;
            double y = anchor.y + 0.03 + (i % 3) * 0.006;
            double z = anchor.z + Math.sin(angle) * radius;
            level.addParticle(new DustParticleOptions(unpackRgb(cream), 0.68f),
                x, y, z, 0.0, 0.005, 0.0);
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.ENCHANTED_HIT,
                anchor.x, anchor.y + 0.04, anchor.z, 0.05, 0.03, 0.05);
        }
    }

    /** Linear blend between two packed RGB colors. */
    private static int blendColor(int from, int to, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int fr = (from >> 16) & 0xFF;
        int fg = (from >>  8) & 0xFF;
        int fb = (from      ) & 0xFF;
        int tr = (to >> 16) & 0xFF;
        int tg = (to >>  8) & 0xFF;
        int tb = (to      ) & 0xFF;
        int r = Math.round(fr + (tr - fr) * t);
        int g = Math.round(fg + (tg - fg) * t);
        int b = Math.round(fb + (tb - fb) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** Unpacks 0xRRGGBB into a normalized RGB vector for DustParticleOptions. */
    private static org.joml.Vector3f unpackRgb(int packed) {
        return new org.joml.Vector3f(
            ((packed >> 16) & 0xFF) / 255f,
            ((packed >>  8) & 0xFF) / 255f,
            ( packed        & 0xFF) / 255f
        );
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MusubiBlockEntities.SPOOL_RACK.get(), SpoolRackBER::new);
        event.registerBlockEntityRenderer(MusubiBlockEntities.SPINNING_WHEEL.get(), SpinningWheelBER::new);
        event.registerBlockEntityRenderer(MusubiBlockEntities.SHINBOKU.get(), ShinbokuBER::new);
        event.registerBlockEntityRenderer(MusubiBlockEntities.MARUDAI.get(), MarudaiBER::new);
        event.registerBlockEntityRenderer(MusubiBlockEntities.ITOMAKI.get(), ItomakiBER::new);
    }

    /** The spinning wheel's rotating disc is its own model, not tied to any blockstate,
     *  so it must be registered as a standalone model to get baked. */
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(SpinningWheelBER.WHEEL_MODEL);
        event.register(MarudaiBER.TAMA_MODEL);
    }

    public static void registerGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAboveAll(
            ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, "tama_fill"),
            com.sanhiruzu.musubi.client.TamaHudOverlay::render);
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

        // Tama — each wound strand (tintindex 1..4) takes the colour of the matching stored type;
        // the bobbin body (tintindex -1) is left untinted.
        net.minecraft.client.color.item.ItemColor tamaColors = (stack, tintIndex) -> {
            if (tintIndex <= 0) return -1;
            var storage = stack.getOrDefault(MusubiDataComponents.TAMA_STORAGE,
                com.sanhiruzu.musubi.component.TamaStorage.EMPTY);
            int i = tintIndex - 1;
            if (i >= storage.entries().size()) return -1;
            return getDominantColor(storage.entries().get(i).itemId()) | 0xFF000000;
        };
        event.register(tamaColors,
            MusubiItems.BONE_TAMA.get(), MusubiItems.IRON_TAMA.get(),
            MusubiItems.SILVER_TAMA.get(), MusubiItems.ORIHIME_TAMA.get());
    }

    // ── Rainbow ───────────────────────────────────────────────────────────────

    public static int rainbowColor() {
        Minecraft mc = Minecraft.getInstance();
        long tick = (mc.level != null) ? mc.level.getGameTime() : System.currentTimeMillis() / 50L;
        float hue = (tick % 200L) / 200.0f;  // full cycle every 200 ticks (10 s)
        return java.awt.Color.HSBtoRGB(hue, 0.65f, 1.0f) | 0xFF000000;
    }

    // ── Dominant color from item texture ──────────────────────────────────────

    public static int getDominantColor(ResourceLocation itemId) {
        return ITEM_COLOR_CACHE.computeIfAbsent(itemId, MusubiClient::computeDominantColor);
    }

    /**
     * Samples the item's sprite once and returns a small palette: a base tone, a stronger accent,
     * and a bright sheen. This is intended for VFX that should feel like they are using the item's
     * actual colors rather than a generic tint.
     */
    public static ItemColorPalette sampleItemPalette(ResourceLocation itemId) {
        return ITEM_PALETTE_CACHE.computeIfAbsent(itemId, MusubiClient::computeItemPalette);
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

    private static ItemColorPalette computeItemPalette(ResourceLocation itemId) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (item == null || item == Items.AIR) {
                return ItemColorPalette.fallback();
            }

            BakedModel model = mc.getItemRenderer().getModel(new ItemStack(item), null, null, 0);
            TextureAtlasSprite sprite = model.getParticleIcon(ModelData.EMPTY);
            SpriteContents contents = sprite.contents();

            Field imgField = SpriteContents.class.getDeclaredField("originalImage");
            imgField.setAccessible(true);
            NativeImage image = (NativeImage) imgField.get(contents);
            if (image == null) return ItemColorPalette.fallback();

            return sampleItemPalette(image, contents.width(), contents.height());
        } catch (Exception e) {
            return ItemColorPalette.fallback();
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

    private static ItemColorPalette sampleItemPalette(NativeImage image, int w, int h) {
        double baseR = 0, baseG = 0, baseB = 0, baseWeight = 0;
        double accentR = 0, accentG = 0, accentB = 0, accentWeight = 0;
        double sheenR = 0, sheenG = 0, sheenB = 0, sheenWeight = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = image.getPixelRGBA(x, y);
                int a = (abgr >> 24) & 0xFF;
                if (a < 96) continue;
                int r = abgr & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
                float sat = hsb[1];
                float bri = hsb[2];

                double baseW = Math.max(0.0, sat) * Math.max(0.2, bri);
                baseR += r * baseW;
                baseG += g * baseW;
                baseB += b * baseW;
                baseWeight += baseW;

                double accentW = Math.pow(sat, 1.6) * Math.pow(bri, 1.15);
                accentR += r * accentW;
                accentG += g * accentW;
                accentB += b * accentW;
                accentWeight += accentW;

                if (bri > 0.68f) {
                    double sheenW = (0.35 + sat * 0.65) * (bri - 0.55);
                    sheenR += r * sheenW;
                    sheenG += g * sheenW;
                    sheenB += b * sheenW;
                    sheenWeight += sheenW;
                }
            }
        }

        int base = baseWeight < 0.01 ? 0xC8A864 : packColor(baseR / baseWeight, baseG / baseWeight, baseB / baseWeight);
        int accent = accentWeight < 0.01 ? base : packColor(accentR / accentWeight, accentG / accentWeight, accentB / accentWeight);
        int sheen = sheenWeight < 0.01 ? 0xF8F1E5 : packColor(sheenR / sheenWeight, sheenG / sheenWeight, sheenB / sheenWeight);
        return new ItemColorPalette(base, accent, sheen);
    }

    private static int packColor(double r, double g, double b) {
        return ((Mth.clamp((int) Math.round(r), 0, 255)) << 16)
            | ((Mth.clamp((int) Math.round(g), 0, 255)) << 8)
            | (Mth.clamp((int) Math.round(b), 0, 255));
    }

    public record ItemColorPalette(int base, int accent, int sheen) {
        public static ItemColorPalette fallback() {
            return new ItemColorPalette(0xC8A864, 0xD8B56A, 0xF8F1E5);
        }
    }
}
