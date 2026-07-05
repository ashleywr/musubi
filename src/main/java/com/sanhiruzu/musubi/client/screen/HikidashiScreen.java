package com.sanhiruzu.musubi.client.screen;

import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.network.HikidashiExtractPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client-side GUI for the Hikidashi storage viewer.
 * Displays a searchable, tier-sortable list of all silk items from connected Spool Racks.
 */
public class HikidashiScreen extends Screen {

    private final BlockPos blockPos;
    private final List<SkeinContents> storage;
    private final List<SkeinContents> filteredStorage;

    private static final String[] TIER_BUTTON_LABELS = { "All", "Feather", "Crinkle", "Rinzu" };

    private EditBox searchField;
    private Button[] tierButtons;
    private int activeFilterTier = -1; // -1 = all tiers

    private static final int SLOT_HEIGHT = 20;
    private static final int SLOT_WIDTH = 200;
    private static final int SLOTS_PER_PAGE = 10;
    private static final int EXTRACT_BUTTON_WIDTH = 50;

    private int scrollOffset = 0;
    private int listStartX = 20;
    private int listStartY = 60;

    public HikidashiScreen(BlockPos blockPos, List<SkeinContents> storage) {
        super(Component.literal("Hikidashi Storage"));
        this.blockPos = blockPos;
        this.storage = new ArrayList<>(storage);
        this.filteredStorage = new ArrayList<>(this.storage);
    }

    @Override
    protected void init() {
        super.init();

        // Search field
        this.searchField = new EditBox(this.font, this.width / 2 - 100, 25, 200, 18, Component.literal("Search"));
        this.searchField.setMaxLength(32);
        this.addRenderableWidget(this.searchField);
        this.setInitialFocus(this.searchField);

        // Tier filter buttons
        this.tierButtons = new Button[4];
        int buttonX = this.width - 200;
        int buttonY = 25;

        tierButtons[0] = this.addRenderableWidget(new Button.Builder(Component.literal("All"), btn -> setTierFilter(-1))
            .pos(buttonX, buttonY)
            .width(45)
            .build());

        tierButtons[1] = this.addRenderableWidget(new Button.Builder(Component.literal("Feather"), btn -> setTierFilter(0))
            .pos(buttonX + 50, buttonY)
            .width(45)
            .build());

        tierButtons[2] = this.addRenderableWidget(new Button.Builder(Component.literal("Crinkle"), btn -> setTierFilter(1))
            .pos(buttonX + 100, buttonY)
            .width(45)
            .build());

        tierButtons[3] = this.addRenderableWidget(new Button.Builder(Component.literal("Rinzu"), btn -> setTierFilter(2))
            .pos(buttonX + 150, buttonY)
            .width(45)
            .build());

        updateFilter();
    }

    private void setTierFilter(int tier) {
        this.activeFilterTier = tier;
        updateFilter();
    }

    private void updateFilter() {
        String search = this.searchField.getValue().toLowerCase(Locale.ROOT);
        this.filteredStorage.clear();

        for (SkeinContents skein : this.storage) {
            // Filter by tier
            if (this.activeFilterTier >= 0 && skein.needleTier() != this.activeFilterTier) {
                continue;
            }

            // Filter by search term
            if (!search.isEmpty()) {
                boolean matches = false;
                for (SkeinContents.VariantEntry variant : skein.variants()) {
                    if (variant.itemId().getPath().toLowerCase(Locale.ROOT).contains(search)) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) continue;
            }

            this.filteredStorage.add(skein);
        }
        this.scrollOffset = 0;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.renderBackground(guiGraphics, pMouseX, pMouseY, pPartialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        // Draw REI cost indicator
        guiGraphics.drawString(this.font, "§6REI Cost: §f10 per extraction", this.width - 150, 8, 0xFFFFFF);

        // Draw search and filter UI
        this.searchField.render(guiGraphics, pMouseX, pMouseY, pPartialTick);

        // Highlight the active tier button (use fixed base labels to avoid prefix accumulation).
        for (int i = 0; i < tierButtons.length; i++) {
            int tierForButton = i - 1;
            tierButtons[i].setMessage(Component.literal(
                (tierForButton == this.activeFilterTier ? "§a" : "") + TIER_BUTTON_LABELS[i]
            ));
        }

        // Draw storage list background
        guiGraphics.fill(listStartX - 5, listStartY - 5, listStartX + SLOT_WIDTH + 5,
            listStartY + SLOTS_PER_PAGE * SLOT_HEIGHT + 5, 0xFF8B7355);

        // Draw storage list
        int visibleCount = Math.min(SLOTS_PER_PAGE, Math.max(0, filteredStorage.size() - scrollOffset));
        for (int i = 0; i < visibleCount; i++) {
            int index = scrollOffset + i;
            if (index >= filteredStorage.size()) break;
            SkeinContents skein = filteredStorage.get(index);
            drawSkeinEntry(guiGraphics, listStartX, listStartY + i * SLOT_HEIGHT, skein, pMouseX, pMouseY);
        }

        // Draw empty message if no results
        if (filteredStorage.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "No items found",
                listStartX + SLOT_WIDTH / 2, listStartY + SLOTS_PER_PAGE * SLOT_HEIGHT / 2, 0xAAAAAA);
        }

        super.render(guiGraphics, pMouseX, pMouseY, pPartialTick);
    }

    private void drawSkeinEntry(GuiGraphics guiGraphics, int x, int y, SkeinContents skein, int mouseX, int mouseY) {
        if (skein.variants().isEmpty()) return;

        String itemName = skein.variants().get(0).itemId().getPath();
        String countText = "×" + skein.count();
        String tierText = getTierName(skein.needleTier());

        // Background
        int bgColor = (mouseX >= x && mouseX < x + SLOT_WIDTH && mouseY >= y && mouseY < y + SLOT_HEIGHT)
            ? 0xFF9C6A45 : 0xFF8B6F47;
        guiGraphics.fill(x, y, x + SLOT_WIDTH, y + SLOT_HEIGHT, bgColor);

        // Item name and tier
        guiGraphics.drawString(this.font, itemName, x + 5, y + 2, 0xFFFFFF);
        guiGraphics.drawString(this.font, tierText, x + 5, y + 11, 0xAAAAFF);

        // Count
        guiGraphics.drawString(this.font, countText, x + SLOT_WIDTH - 40, y + 6, 0xDDDDDD);

        // Extract button
        int buttonX = x + SLOT_WIDTH - 35;
        int buttonY = y + 2;
        boolean buttonHovered = mouseX >= buttonX && mouseX < buttonX + EXTRACT_BUTTON_WIDTH &&
                               mouseY >= buttonY && mouseY < buttonY + 16;

        int btnColor = buttonHovered ? 0xFF4CAF50 : 0xFF388E3C;
        guiGraphics.fill(buttonX, buttonY, buttonX + EXTRACT_BUTTON_WIDTH, buttonY + 16, btnColor);
        guiGraphics.drawCenteredString(this.font, "Extract", buttonX + EXTRACT_BUTTON_WIDTH / 2, buttonY + 4, 0xFFFFFF);

        // Store button bounds for click detection
        if (buttonHovered && this.minecraft != null) {
            guiGraphics.fill(buttonX, buttonY, buttonX + EXTRACT_BUTTON_WIDTH, buttonY + 16, 0xFF66BB6A);
        }
    }

    private String getTierName(int tier) {
        return switch (tier) {
            case 0 -> "Feather";
            case 1 -> "Crinkle";
            case 2 -> "Rinzu";
            default -> "Unknown";
        };
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
        // Only scroll if mouse is over the list area
        if (pMouseX >= listStartX && pMouseX < listStartX + SLOT_WIDTH &&
            pMouseY >= listStartY && pMouseY < listStartY + SLOTS_PER_PAGE * SLOT_HEIGHT) {
            int maxScroll = Math.max(0, filteredStorage.size() - SLOTS_PER_PAGE);
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - (int) pScrollY));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        // Handle extract buttons
        if (pMouseX >= listStartX && pMouseX < listStartX + SLOT_WIDTH &&
            pMouseY >= listStartY && pMouseY < listStartY + SLOTS_PER_PAGE * SLOT_HEIGHT) {

            int slotIndex = (int) ((pMouseY - listStartY) / SLOT_HEIGHT);
            int itemIndex = scrollOffset + slotIndex;

            if (itemIndex >= 0 && itemIndex < filteredStorage.size()) {
                SkeinContents skein = filteredStorage.get(itemIndex);
                if (skein.variants().isEmpty()) return super.mouseClicked(pMouseX, pMouseY, pButton);

                int buttonX = listStartX + SLOT_WIDTH - 35;
                int buttonY = listStartY + slotIndex * SLOT_HEIGHT + 2;

                if (pMouseX >= buttonX && pMouseX < buttonX + EXTRACT_BUTTON_WIDTH &&
                    pMouseY >= buttonY && pMouseY < buttonY + 16) {

                    int amount = this.minecraft != null && this.minecraft.player != null && this.minecraft.player.isShiftKeyDown()
                        ? 64 : 1;
                    extractItem(skein.variants().get(0).itemId(), amount);
                    return true;
                }
            }
        }

        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    private void extractItem(net.minecraft.resources.ResourceLocation itemId, int amount) {
        var payload = new HikidashiExtractPayload(blockPos, itemId, amount);
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        if (this.searchField.keyPressed(pKeyCode, pScanCode, pModifiers)) {
            updateFilter();
            return true;
        }
        if (pKeyCode == 256) { // ESC
            this.onClose();
            return true;
        }
        return super.keyPressed(pKeyCode, pScanCode, pModifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
