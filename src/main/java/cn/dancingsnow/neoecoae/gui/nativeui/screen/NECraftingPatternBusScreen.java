package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import static cn.dancingsnow.neoecoae.gui.nativeui.layout.NECraftingPatternBusLayout.*;

import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.EncodedPatternItem;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NECraftingPatternBusMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Screen for the ECO Crafting Pattern Bus — 9×7 pattern slots (63 total),
 * using AE2-style panels and slot backgrounds.
 */
public class NECraftingPatternBusScreen extends AbstractContainerScreen<NECraftingPatternBusMenu> {

    // ── Text colors ── (uses NENativeUiConstants.MACHINE_TEXT_PRIMARY)

    // Ghost
    private static final float GHOST_ALPHA = 0.10f;

    private final ItemStack ghostPattern;
    private Button previousPageButton;
    private Button nextPageButton;

    /** Cache decoded pattern outputs keyed by "itemRegistryKey|nbt". Avoids repeated AE2 decoding each frame. */
    private final Map<String, ItemStack> patternDisplayCache = new HashMap<>();

    public NECraftingPatternBusScreen(NECraftingPatternBusMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
        this.ghostPattern = AEItems.BLANK_PATTERN.stack();
    }

    @Override
    protected void init() {
        super.init();
        this.previousPageButton = addRenderableWidget(Button.builder(
                        Component.literal("<"), button -> changePage(NECraftingPatternBusMenu.BUTTON_PREVIOUS_PAGE))
                .bounds(leftPos + 126, topPos + 4, 12, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.neoecoae.pattern_bus.previous_page")))
                .build());
        this.nextPageButton = addRenderableWidget(
                Button.builder(Component.literal(">"), button -> changePage(NECraftingPatternBusMenu.BUTTON_NEXT_PAGE))
                        .bounds(leftPos + 158, topPos + 4, 12, 14)
                        .tooltip(Tooltip.create(Component.translatable("gui.neoecoae.pattern_bus.next_page")))
                        .build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        // Temporarily swap pattern items with their decoded output so that
        // super.render() draws the product icon instead of the encoded-pattern icon.
        int limit = Math.min(NECraftingPatternBusMenu.PATTERN_SLOTS, menu.slots.size());
        ItemStack[] originals = new ItemStack[limit];
        for (int i = 0; i < limit; i++) {
            Slot slot = menu.getSlot(i);
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty() && stack.getItem() instanceof EncodedPatternItem) {
                ItemStack display = getPatternDisplay(stack);
                if (!display.isEmpty()) {
                    originals[i] = stack.copy();
                    slot.set(display);
                }
            }
        }

        updatePageButtons();
        super.render(g, mouseX, mouseY, partialTick);

        // Restore original encoded-pattern ItemStacks
        for (int i = 0; i < limit; i++) {
            if (originals[i] != null) {
                menu.getSlot(i).set(originals[i]);
            }
        }

        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // 1. Main background (AE2 generated panel)
        NENativeAe2StyleRenderer.drawAeMainPanel(g, x, y, GUI_W, GUI_H);

        // 2. Pattern area slots
        for (int row = 0; row < PATTERN_ROWS; row++) {
            for (int col = 0; col < PATTERN_COLS; col++) {
                NENativeAe2StyleRenderer.drawAeSlot(
                        g, x + PATTERN_BG_X + col * SLOT_SIZE, y + PATTERN_BG_Y + row * SLOT_SIZE);
            }
        }

        // 3. Player inventory slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                NENativeAe2StyleRenderer.drawAeSlot(g, x + INV_BG_X + col * SLOT_SIZE, y + INV_BG_Y + row * SLOT_SIZE);
            }
        }

        // 4. Hotbar slots
        for (int col = 0; col < 9; col++) {
            NENativeAe2StyleRenderer.drawAeSlot(g, x + HOTBAR_BG_X + col * SLOT_SIZE, y + HOTBAR_BG_Y);
        }

        // 5. Ghost pattern hints (pattern slots only)
        RenderSystem.enableBlend();
        for (int i = 0; i < NECraftingPatternBusMenu.PATTERN_SLOTS; i++) {
            if (i < menu.slots.size() && !menu.getSlot(i).hasItem()) {
                int col = i % PATTERN_COLS;
                int row = i / PATTERN_COLS;
                int gx = x + PATTERN_SLOT_X + col * SLOT_SIZE;
                int gy = y + PATTERN_SLOT_Y + row * SLOT_SIZE;
                RenderSystem.setShaderColor(1, 1, 1, GHOST_ALPHA);
                g.renderItem(ghostPattern, gx, gy);
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
        }
        RenderSystem.disableBlend();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        boolean paged = menu.getPageCount() > 1;
        Component displayTitle = title;
        if (paged && font.width(title) > 112) {
            displayTitle = Component.literal(font.plainSubstrByWidth(title.getString(), 104) + "...");
        }
        g.drawString(font, displayTitle, TITLE_X, TITLE_Y, NENativeUiConstants.MACHINE_TEXT_PRIMARY, false);
        if (paged) {
            String pageText = (menu.getCurrentPage() + 1) + " / " + menu.getPageCount();
            g.drawCenteredString(font, pageText, 148, TITLE_Y, NENativeUiConstants.MACHINE_TEXT_PRIMARY);
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
        previousPageButton.visible = visible;
        nextPageButton.visible = visible;
        previousPageButton.active = visible && page > 0;
        nextPageButton.active = visible && page + 1 < pages;
    }

    /**
     * Decode an AE2 encoded pattern and return its primary output ItemStack.
     * Results are cached per unique pattern NBT to avoid repeated decoding.
     * Returns {@code ItemStack.EMPTY} on failure so the original pattern icon
     * remains visible as a fallback.
     */
    private ItemStack getPatternDisplay(ItemStack patternStack) {
        if (!(patternStack.getItem() instanceof EncodedPatternItem patternItem)) {
            return ItemStack.EMPTY;
        }
        String tagKey = patternStack.getTag() != null ? patternStack.getTag().toString() : "{}";
        String key = BuiltInRegistries.ITEM.getKey(patternStack.getItem()) + "|" + tagKey;
        ItemStack cached = patternDisplayCache.get(key);
        if (cached != null) return cached.copy();
        try {
            ItemStack output = patternItem.getOutput(patternStack);
            if (!output.isEmpty()) {
                patternDisplayCache.put(key, output.copy());
                return output;
            }
        } catch (Exception ignored) {
            // Broken or unparseable pattern — fall back to showing the pattern item itself
        }
        return ItemStack.EMPTY;
    }
}
