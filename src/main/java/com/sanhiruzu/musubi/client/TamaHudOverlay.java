package com.sanhiruzu.musubi.client;

import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.TamaStorage;
import com.sanhiruzu.musubi.item.TamaItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * A small Tama fill-bar that flashes near the top of the screen when the player presses the
 * attune/peek key, then fades. Reads the active tama's synced storage entirely client-side.
 */
public final class TamaHudOverlay {

    private TamaHudOverlay() {}

    private static final long PEEK_TICKS = 60L; // ~3 seconds
    private static long showUntilTick = Long.MIN_VALUE;

    /** Show the bar for the next few seconds. */
    public static void peek() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) showUntilTick = mc.level.getGameTime() + PEEK_TICKS;
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (mc.level.getGameTime() > showUntilTick) return;

        ItemStack tama = TamaItem.findActiveTama(mc.player);
        if (tama == null || !(tama.getItem() instanceof TamaItem t)) return;

        TamaStorage storage = tama.getOrDefault(MusubiDataComponents.TAMA_STORAGE, TamaStorage.EMPTY);
        int total = storage.total();
        int cap = t.storageCapacity(tama);
        float frac = cap <= 0 ? 0f : Mth.clamp(total / (float) cap, 0f, 1f);

        int barW = 90, barH = 5;
        int sw = graphics.guiWidth();
        int x = (sw - barW) / 2;
        int y = 8;

        // Track + border.
        graphics.fill(x - 1, y - 1, x + barW + 1, y + barH + 1, 0xC0000000);
        graphics.fill(x, y, x + barW, y + barH, 0xFF2B2B2B);

        // Fill — silk aqua, warming to amber then red as it nears full.
        int color = frac >= 1f ? 0xFFE0533C : frac >= 0.9f ? 0xFFE0A53C : 0xFF8CD8E0;
        int fillW = Math.round(barW * frac);
        if (fillW > 0) graphics.fill(x, y, x + fillW, y + barH, color);

        // Label above the bar.
        Component label = Component.translatable("musubi.tama.hud", total, cap);
        graphics.drawCenteredString(mc.font, label, sw / 2, y - 10, 0xFFFFFFFF);
    }
}
