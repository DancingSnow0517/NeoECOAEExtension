package cn.dancingsnow.neoecoae.gui.ldlib;

import static cn.dancingsnow.neoecoae.gui.nativeui.layout.NECraftingPatternBusLayout.*;

import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.EncodedPatternItem;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NECraftingPatternBusMenu;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NENativeAe2StyleRenderer;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectAndBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * LDLib1 client layer for the Crafting Pattern Bus.
 *
 * <p>The existing paged menu owns all real slots and shift-click behavior. This
 * screen overlays the decoded output icon without replacing the encoded
 * pattern ItemStack stored in the slot.
 */
public class NECraftingPatternBusLDLibUI extends NELDLibMachineScreen<NECraftingPatternBusMenu> {
    private static final int PAGE_BUTTON_Y = 4;
    private static final int PAGE_BUTTON_W = 12;
    private static final int PAGE_BUTTON_H = 14;
    private static final int PAGE_PREV_BUTTON_X = GUI_W - 34;
    private static final int PAGE_NEXT_BUTTON_X = GUI_W - 16;
    private static final int PAGE_TEXT_RIGHT_X = PAGE_PREV_BUTTON_X - 5;
    private static final int PAGE_TEXT_COLOR = 0xFFFFD24A;
    private static final float GHOST_ALPHA = 0.10F;

    private static final IGuiTexture BUTTON_TEXTURE =
            new GuiTextureGroup(new ColorRectAndBorderTexture(0xFFECEEF3, 0xFF707070, 1.0F));
    private static final IGuiTexture BUTTON_HOVER_TEXTURE =
            new GuiTextureGroup(new ColorRectAndBorderTexture(0x00000000, 0xFF4F7FB6, 1.0F));

    private final ItemStack ghostPattern;
    private final Map<String, ItemStack> patternDisplayCache = new HashMap<>();
    private ButtonWidget previousPageButton;
    private ButtonWidget nextPageButton;

    public NECraftingPatternBusLDLibUI(NECraftingPatternBusMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, GUI_W, GUI_H);
        this.ghostPattern = AEItems.BLANK_PATTERN.stack();
    }

    @Override
    protected boolean shouldAddTitleWidget() {
        return false;
    }

    @Override
    protected void initLdWidgets() {
        this.previousPageButton = addLdWidget(new ButtonWidget(
                        PAGE_PREV_BUTTON_X,
                        PAGE_BUTTON_Y,
                        PAGE_BUTTON_W,
                        PAGE_BUTTON_H,
                        BUTTON_TEXTURE,
                        click -> changePage(NECraftingPatternBusMenu.BUTTON_PREVIOUS_PAGE)))
                .setHoverTexture(BUTTON_HOVER_TEXTURE);
        this.nextPageButton = addLdWidget(new ButtonWidget(
                        PAGE_NEXT_BUTTON_X,
                        PAGE_BUTTON_Y,
                        PAGE_BUTTON_W,
                        PAGE_BUTTON_H,
                        BUTTON_TEXTURE,
                        click -> changePage(NECraftingPatternBusMenu.BUTTON_NEXT_PAGE)))
                .setHoverTexture(BUTTON_HOVER_TEXTURE);
        updatePageButtons();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        updatePageButtons();
    }

    @Override
    protected void renderLdBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updatePageButtons();
        NENativeAe2StyleRenderer.drawAeMainPanel(g, leftPos, topPos, GUI_W, GUI_H);

        for (int row = 0; row < PATTERN_ROWS; row++) {
            for (int col = 0; col < PATTERN_COLS; col++) {
                NENativeAe2StyleRenderer.drawAeSlot(
                        g, leftPos + PATTERN_BG_X + col * SLOT_SIZE, topPos + PATTERN_BG_Y + row * SLOT_SIZE);
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                NENativeAe2StyleRenderer.drawAeSlot(
                        g, leftPos + INV_BG_X + col * SLOT_SIZE, topPos + INV_BG_Y + row * SLOT_SIZE);
            }
        }

        for (int col = 0; col < 9; col++) {
            NENativeAe2StyleRenderer.drawAeSlot(g, leftPos + HOTBAR_BG_X + col * SLOT_SIZE, topPos + HOTBAR_BG_Y);
        }

        drawGhostPatterns(g);
    }

    @Override
    protected void renderLdForeground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawPatternDisplayOverlays(g);
        drawTitleAndPage(g);
        drawPageButtonText(g);
    }

    @Override
    protected void renderLdTooltips(GuiGraphics g, int mouseX, int mouseY) {
        if (previousPageButton != null
                && previousPageButton.isVisible()
                && previousPageButton.isMouseOverElement(mouseX, mouseY)) {
            g.renderComponentTooltip(
                    font,
                    java.util.List.of(Component.translatable("gui.neoecoae.pattern_bus.previous_page")),
                    mouseX,
                    mouseY);
            return;
        }
        if (nextPageButton != null && nextPageButton.isVisible() && nextPageButton.isMouseOverElement(mouseX, mouseY)) {
            g.renderComponentTooltip(
                    font,
                    java.util.List.of(Component.translatable("gui.neoecoae.pattern_bus.next_page")),
                    mouseX,
                    mouseY);
        }
    }

    private void changePage(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void updatePageButtons() {
        int pages = menu.getPageCount();
        int page = menu.getCurrentPage();
        boolean visible = pages > 1;

        if (previousPageButton != null) {
            previousPageButton.setVisible(visible);
            previousPageButton.setActive(visible && page > 0);
        }
        if (nextPageButton != null) {
            nextPageButton.setVisible(visible);
            nextPageButton.setActive(visible && page + 1 < pages);
        }
    }

    private void drawGhostPatterns(GuiGraphics g) {
        RenderSystem.enableBlend();
        for (int i = 0; i < NECraftingPatternBusMenu.PATTERN_SLOTS; i++) {
            if (i < menu.slots.size() && !menu.getSlot(i).hasItem()) {
                int col = i % PATTERN_COLS;
                int row = i / PATTERN_COLS;
                RenderSystem.setShaderColor(1, 1, 1, GHOST_ALPHA);
                g.renderItem(
                        ghostPattern,
                        leftPos + PATTERN_SLOT_X + col * SLOT_SIZE,
                        topPos + PATTERN_SLOT_Y + row * SLOT_SIZE);
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
        }
        RenderSystem.disableBlend();
    }

    private void drawPatternDisplayOverlays(GuiGraphics g) {
        int limit = Math.min(NECraftingPatternBusMenu.PATTERN_SLOTS, menu.slots.size());
        for (int i = 0; i < limit; i++) {
            Slot slot = menu.getSlot(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !(stack.getItem() instanceof EncodedPatternItem)) {
                continue;
            }
            ItemStack display = getPatternDisplay(stack);
            if (display.isEmpty()) {
                continue;
            }
            g.renderItem(display, leftPos + slot.x, topPos + slot.y);
            g.renderItemDecorations(font, display, leftPos + slot.x, topPos + slot.y);
        }
    }

    private void drawTitleAndPage(GuiGraphics g) {
        boolean paged = menu.getPageCount() > 1;
        String pageText = paged ? (menu.getCurrentPage() + 1) + " / " + menu.getPageCount() : "";

        int titleMaxWidth;
        if (paged) {
            int pageTextX = PAGE_TEXT_RIGHT_X - font.width(pageText);
            titleMaxWidth = Math.max(0, pageTextX - TITLE_X - 6);
        } else {
            titleMaxWidth = GUI_W - TITLE_X - 8;
        }

        Component displayTitle = truncateTitle(title, titleMaxWidth);
        g.drawString(
                font,
                displayTitle,
                leftPos + TITLE_X,
                topPos + TITLE_Y,
                NENativeUiConstants.MACHINE_TEXT_PRIMARY,
                false);

        if (paged) {
            int pageTextX = PAGE_TEXT_RIGHT_X - font.width(pageText);
            g.drawString(font, pageText, leftPos + pageTextX, topPos + TITLE_Y, PAGE_TEXT_COLOR, true);
        }
    }

    private void drawPageButtonText(GuiGraphics g) {
        if (previousPageButton != null && previousPageButton.isVisible()) {
            int color = previousPageButton.isActive() ? TEXT_PRIMARY : TEXT_MUTED;
            g.drawString(font, "<", leftPos + PAGE_PREV_BUTTON_X + 3, topPos + PAGE_BUTTON_Y + 3, color, false);
        }
        if (nextPageButton != null && nextPageButton.isVisible()) {
            int color = nextPageButton.isActive() ? TEXT_PRIMARY : TEXT_MUTED;
            g.drawString(font, ">", leftPos + PAGE_NEXT_BUTTON_X + 3, topPos + PAGE_BUTTON_Y + 3, color, false);
        }
    }

    private Component truncateTitle(Component text, int maxWidth) {
        String raw = text.getString();
        if (maxWidth <= 0) {
            return Component.empty();
        }
        if (font.width(raw) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (maxWidth <= ellipsisWidth) {
            return Component.literal(font.plainSubstrByWidth(raw, maxWidth));
        }

        return Component.literal(font.plainSubstrByWidth(raw, maxWidth - ellipsisWidth) + ellipsis);
    }

    private ItemStack getPatternDisplay(ItemStack patternStack) {
        if (!(patternStack.getItem() instanceof EncodedPatternItem patternItem)) {
            return ItemStack.EMPTY;
        }

        String tagKey = patternStack.getTag() != null ? patternStack.getTag().toString() : "{}";
        String key = BuiltInRegistries.ITEM.getKey(patternStack.getItem()) + "|" + tagKey;
        ItemStack cached = patternDisplayCache.get(key);
        if (cached != null) {
            return cached.copy();
        }

        try {
            ItemStack output = patternItem.getOutput(patternStack);
            if (!output.isEmpty()) {
                patternDisplayCache.put(key, output.copy());
                return output;
            }
        } catch (Exception ignored) {
            // Keep the encoded-pattern icon visible for invalid or stale pattern data.
        }

        return ItemStack.EMPTY;
    }
}
