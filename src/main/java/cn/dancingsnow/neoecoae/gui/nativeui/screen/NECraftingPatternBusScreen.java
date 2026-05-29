package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.nativeui.NENineSliceRenderer;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NECraftingPatternBusMenu;
import appeng.core.definitions.AEItems;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Screen for the ECO Crafting Pattern Bus — 9×7 pattern slots (63 total),
 * three clearly separated bordered groups, weak ghost hints.
 * <p>
 * Layout aligned with the 1.21.1 native-ui visual style:
 *   - main bg via nine-slice background.png
 *   - pattern area, player inventory, and hotbar each have their own inventory_border
 *   - ghost blank-pattern icon at alpha 0.18 on empty pattern slots only
 * </p>
 */
public class NECraftingPatternBusScreen extends AbstractContainerScreen<NECraftingPatternBusMenu> {

    // ── Textures ──────────────────────────────────────────────
    private static final ResourceLocation TEX_BG         = NeoECOAE.id("textures/gui/background.png");
    private static final ResourceLocation TEX_SLOT       = NeoECOAE.id("textures/gui/slot.png");
    private static final ResourceLocation TEX_INV_BORDER = NeoECOAE.id("textures/gui/inventory_border.png");

    // ── Panel dimensions ─────────────────────────────────────
    private static final int GUI_W = 172;
    private static final int GUI_H = 246;

    // ── Layout constants (Menu must use identical values) ────
    private static final int PATTERN_COLS = 9;
    private static final int PATTERN_ROWS = 7;

    private static final int SLOT_SIZE = 18;

    // Pattern area
    private static final int PATTERN_BORDER_X = 4;
    private static final int PATTERN_BORDER_Y = 27;
    private static final int PATTERN_BORDER_W = PATTERN_COLS * SLOT_SIZE + 2;  // 164
    private static final int PATTERN_BORDER_H = PATTERN_ROWS * SLOT_SIZE + 2;  // 128
    private static final int PATTERN_SLOT_X   = PATTERN_BORDER_X + 1;          // 5
    private static final int PATTERN_SLOT_Y   = PATTERN_BORDER_Y + 1;          // 28

    // Player inventory area
    private static final int INV_BORDER_X = 4;
    private static final int INV_BORDER_Y = 160;
    private static final int INV_BORDER_W = 9 * SLOT_SIZE + 2;   // 164
    private static final int INV_BORDER_H = 3 * SLOT_SIZE + 2;   // 56
    private static final int INV_SLOT_X   = INV_BORDER_X + 1;    // 5
    private static final int INV_SLOT_Y   = INV_BORDER_Y + 1;    // 161

    // Hotbar area
    private static final int HOTBAR_BORDER_X = 4;
    private static final int HOTBAR_BORDER_Y = 219;
    private static final int HOTBAR_BORDER_W = 9 * SLOT_SIZE + 2;  // 164
    private static final int HOTBAR_BORDER_H = 1 * SLOT_SIZE + 2;  // 20
    private static final int HOTBAR_SLOT_X   = HOTBAR_BORDER_X + 1; // 5
    private static final int HOTBAR_SLOT_Y   = HOTBAR_BORDER_Y + 1; // 220

    // Title
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 5;
    private static final int TXT_PRIMARY = 0xFF403E53;

    // Ghost
    private static final float GHOST_ALPHA = 0.18f;

    private final ItemStack ghostPattern;

    public NECraftingPatternBusScreen(NECraftingPatternBusMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
        this.ghostPattern = AEItems.BLANK_PATTERN.stack();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // ── 1. Main background (nine-slice) ──────────────────
        NENineSliceRenderer.drawPanel(g, TEX_BG, x, y, GUI_W, GUI_H,
            16, 16, 2, 2, 2, 4);

        // ── 2. Pattern area (independent bordered group) ──────
        NENineSliceRenderer.drawPanel(g, TEX_INV_BORDER,
            x + PATTERN_BORDER_X, y + PATTERN_BORDER_Y,
            PATTERN_BORDER_W, PATTERN_BORDER_H,
            16, 16, 1, 1, 1, 1);

        for (int row = 0; row < PATTERN_ROWS; row++) {
            for (int col = 0; col < PATTERN_COLS; col++) {
                drawSlot(g, x + PATTERN_SLOT_X + col * SLOT_SIZE,
                              y + PATTERN_SLOT_Y + row * SLOT_SIZE);
            }
        }

        // ── 3. Player inventory (independent bordered group) ──
        NENineSliceRenderer.drawPanel(g, TEX_INV_BORDER,
            x + INV_BORDER_X, y + INV_BORDER_Y,
            INV_BORDER_W, INV_BORDER_H,
            16, 16, 1, 1, 1, 1);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, x + INV_SLOT_X + col * SLOT_SIZE,
                              y + INV_SLOT_Y + row * SLOT_SIZE);
            }
        }

        // ── 4. Hotbar (independent bordered group) ───────────
        NENineSliceRenderer.drawPanel(g, TEX_INV_BORDER,
            x + HOTBAR_BORDER_X, y + HOTBAR_BORDER_Y,
            HOTBAR_BORDER_W, HOTBAR_BORDER_H,
            16, 16, 1, 1, 1, 1);

        for (int col = 0; col < 9; col++) {
            drawSlot(g, x + HOTBAR_SLOT_X + col * SLOT_SIZE,
                          y + HOTBAR_SLOT_Y);
        }

        // ── 5. Ghost pattern hints (pattern slots only) ──────
        // Must use the same coordinates as Menu Slot positions — no +1 offset.
        // Real items are rendered at Slot.x / Slot.y by the container screen.
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

    private void drawSlot(GuiGraphics g, int sx, int sy) {
        g.blit(TEX_SLOT, sx, sy, 0, 0, 18, 18, 18, 18);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, TITLE_X, TITLE_Y, TXT_PRIMARY, false);
    }
}
