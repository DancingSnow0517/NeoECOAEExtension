package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import appeng.client.gui.Icon;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

/**
 * AE2-style rendering helpers for ECO machine native UIs.
 * <p>
 * All drawing methods reference AE2 namespace resources (textures, Icon enum)
 * so that machine GUIs follow AE2 resource-pack changes automatically.
 * No project GUI textures are used here.
 * </p>
 */
public final class NENativeAe2StyleRenderer {

    private NENativeAe2StyleRenderer() {
    }

    // ── AE2 common resources ──

    /** AE2 states.png — main 256×256 panel/slot atlas. */
    public static final ResourceLocation AE_STATES = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/states.png");
    /** AE2 extra_panels.png — upgrade panel atlas. */
    public static final ResourceLocation AE_EXTRA_PANELS = ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/extra_panels.png");

    public static final int STATES_TEX_W = 256;
    public static final int STATES_TEX_H = 256;
    public static final int EXTRA_TEX_W = 128;
    public static final int EXTRA_TEX_H = 128;

    // AE2 main panel border constants (from AE2's ScreenStyle / AEBaseScreen)
    private static final int PANEL_BORDER_LEFT = 0;
    private static final int PANEL_BORDER_TOP = 0;
    private static final int PANEL_BORDER_RIGHT = 0;
    private static final int PANEL_BORDER_BOTTOM = 2;
    private static final int PANEL_TILE = 16;
    private static final int PANEL_SRC_X = 0;
    private static final int PANEL_SRC_Y = 0;

    // AE2 toolbar panel constants
    private static final int TOOLBAR_SRC_X = 176;
    private static final int TOOLBAR_SRC_Y = 0;
    private static final int TOOLBAR_TILE = 16;
    private static final int TOOLBAR_BORDER_TOP = 0;
    private static final int TOOLBAR_BORDER_BOTTOM = 2;
    private static final int TOOLBAR_BORDER_LEFT = 0;
    private static final int TOOLBAR_BORDER_RIGHT = 0;

    // AE2 toolbar button constants
    private static final int BTN_H_SRC_X = 224;
    private static final int BTN_H_SRC_Y = 0;
    private static final int BTN_D_SRC_X = 240;
    private static final int BTN_D_SRC_Y = 0;
    private static final int BTN_TILE = 16;
    private static final int BTN_BORDER = 2;

    // ── Public drawing API ──

    /** Draw an AE2-style main panel using nine-slice from states.png. */
    public static void drawAeMainPanel(GuiGraphics g, int x, int y, int w, int h) {
        drawNineSlice(g, AE_STATES, x, y, w, h,
            PANEL_TILE, PANEL_TILE,
            PANEL_SRC_X, PANEL_SRC_Y,
            PANEL_BORDER_LEFT, PANEL_BORDER_TOP,
            PANEL_BORDER_RIGHT, PANEL_BORDER_BOTTOM,
            STATES_TEX_W, STATES_TEX_H);
    }

    /** Draw a AE2-style toolbar panel using nine-slice from states.png. */
    public static void drawAeToolbarPanel(GuiGraphics g, int x, int y, int w, int h) {
        drawNineSlice(g, AE_STATES, x, y, w, h,
            TOOLBAR_TILE, TOOLBAR_TILE,
            TOOLBAR_SRC_X, TOOLBAR_SRC_Y,
            TOOLBAR_BORDER_LEFT, TOOLBAR_BORDER_TOP,
            TOOLBAR_BORDER_RIGHT, TOOLBAR_BORDER_BOTTOM,
            STATES_TEX_W, STATES_TEX_H);
    }

    /** Draw an AE2 toolbar button background, with hover/disabled states. */
    public static void drawAeToolbarButtonBackground(GuiGraphics g, int x, int y, int w, int h,
                                                      boolean hovered, boolean active) {
        int srcX = active ? (hovered ? BTN_H_SRC_X : BTN_D_SRC_X) : BTN_D_SRC_X;
        int srcY = active ? BTN_D_SRC_Y : 0; // disabled uses a darker row (srcY=0 for simplicity)
        // For a simple implementation: use toolbar button tiles from states.png
        drawNineSlice(g, AE_STATES, x, y, w, h,
            BTN_TILE, BTN_TILE,
            srcX, srcY,
            BTN_BORDER, BTN_BORDER,
            BTN_BORDER, BTN_BORDER,
            STATES_TEX_W, STATES_TEX_H);
    }

    /** Draw a standard AE2 slot background (Icon.SLOT_BACKGROUND). */
    public static void drawAeSlot(GuiGraphics g, int x, int y) {
        g.blit(Icon.TEXTURE, x, y,
            Icon.SLOT_BACKGROUND.x, Icon.SLOT_BACKGROUND.y,
            Icon.SLOT_BACKGROUND.width, Icon.SLOT_BACKGROUND.height,
            Icon.TEXTURE_WIDTH, Icon.TEXTURE_HEIGHT);
    }

    /** Draw an AE2 Icon at (x, y) with given alpha. */
    public static void drawAeIcon(GuiGraphics g, Icon icon, int x, int y, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(Icon.TEXTURE, x, y,
            icon.x, icon.y,
            icon.width, icon.height,
            Icon.TEXTURE_WIDTH, Icon.TEXTURE_HEIGHT);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Draw an AE2 Icon at (x, y) with full opacity. */
    public static void drawAeIcon(GuiGraphics g, Icon icon, int x, int y) {
        drawAeIcon(g, icon, x, y, 1.0F);
    }

    /**
     * Draw an AE2-style upgrade panel on the right side.
     * Uses extra_panels.png, matching AE2's UpgradesPanel layout.
     *
     * @param g         graphics context
     * @param x         left edge of the upgrade panel area
     * @param y         top edge of the upgrade panel area
     * @param slotCount number of upgrade slots
     */
    public static void drawAeUpgradePanel(GuiGraphics g, int x, int y, int slotCount) {
        final int SLOT_SIZE = 18;
        final int PADDING = 7;

        for (int i = 0; i < slotCount; i++) {
            int srcX = PADDING;
            int srcY = PADDING;
            int srcW = SLOT_SIZE;
            int srcH = SLOT_SIZE;
            int drawX = x;
            int drawY = y + i * SLOT_SIZE;

            boolean borderLeft = true;
            boolean borderRight = true;
            boolean borderTop = i == 0;
            boolean borderBottom = i == slotCount - 1;

            if (borderLeft) {
                drawX -= PADDING;
                srcX = 0;
                srcW += PADDING;
            }
            if (borderRight) {
                srcW += PADDING;
            }
            if (borderTop) {
                drawY -= PADDING;
                srcY = 0;
                srcH += PADDING;
            }
            if (borderBottom) {
                srcH += PADDING;
            }

            g.blit(AE_EXTRA_PANELS, drawX, drawY, srcX, srcY, srcW, srcH,
                EXTRA_TEX_W, EXTRA_TEX_H);
        }
    }

    /**
     * Draw a fluid tank with AE2-style border and fluid texture tiling.
     *
     * @param g        graphics context
     * @param x        tank left
     * @param y        tank top
     * @param w        tank width
     * @param h        tank height
     * @param stack    the FluidStack, may be EMPTY
     * @param amount   current amount in mB
     * @param capacity tank capacity in mB
     */
    public static void drawAeFluidTank(GuiGraphics g, int x, int y, int w, int h,
                                        FluidStack stack, int amount, int capacity) {
        // AE2-style thin border
        int borderColor = 0xFF8B8B8B;
        g.fill(x, y, x + w, y + 1, borderColor);
        g.fill(x, y + h - 1, x + w, y + h, borderColor);
        g.fill(x, y, x + 1, y + h, borderColor);
        g.fill(x + w - 1, y, x + w, y + h, borderColor);

        // Inner area
        int ix = x + 1;
        int iy = y + 1;
        int iw = w - 2;
        int ih = h - 2;

        // Empty background
        g.fill(ix, iy, ix + iw, iy + ih, 0xFF2A2A3A);

        if (amount <= 0 || stack.isEmpty()) return;

        int barH = Mth.clamp((int) ((long) amount * ih / capacity), 1, ih);
        int fillY = iy + ih - barH;
        drawFluidTexture(g, ix, fillY, iw, barH, stack);
    }

    /**
     * Draw a progress bar with AE2-style visuals.
     */
    public static void drawAeProgressBar(GuiGraphics g, int x, int y, int w, int h,
                                          int progress, int maxProgress) {
        // Thin AE2-style border
        int borderColor = 0xFF8B8B8B;
        g.fill(x, y, x + w, y + 1, borderColor);
        g.fill(x, y + h - 1, x + w, y + h, borderColor);
        g.fill(x, y, x + 1, y + h, borderColor);
        g.fill(x + w - 1, y, x + w, y + h, borderColor);

        // Inner area
        int ix = x + 1;
        int iy = y + 1;
        int iw = w - 2;
        int ih = h - 2;

        // Empty background
        g.fill(ix, iy, ix + iw, iy + ih, 0xFF2A2A3A);

        if (maxProgress > 0 && progress > 0) {
            int fillH = Mth.clamp(progress * ih / maxProgress, 1, ih);
            // AE2-style progress fill (blue-tinted)
            g.fill(ix, iy + ih - fillH, ix + iw, iy + ih, 0xFF4A7FD6);
        }
    }

    // ── Internal helpers ──

    private static void drawNineSlice(GuiGraphics g, ResourceLocation tex,
                                       int x, int y, int w, int h,
                                       int tileW, int tileH,
                                       int srcBaseX, int srcBaseY,
                                       int borderL, int borderT,
                                       int borderR, int borderB,
                                       int texW, int texH) {
        int srcCenterW = tileW - borderL - borderR;
        int srcCenterH = tileH - borderT - borderB;

        // Corners
        blit(g, tex, x, y, borderL, borderT,
            srcBaseX, srcBaseY, borderL, borderT, texW, texH);

        blit(g, tex, x + w - borderR, y, borderR, borderT,
            srcBaseX + tileW - borderR, srcBaseY, borderR, borderT, texW, texH);

        blit(g, tex, x, y + h - borderB, borderL, borderB,
            srcBaseX, srcBaseY + tileH - borderB, borderL, borderB, texW, texH);

        blit(g, tex, x + w - borderR, y + h - borderB, borderR, borderB,
            srcBaseX + tileW - borderR, srcBaseY + tileH - borderB, borderR, borderB, texW, texH);

        // Edges
        if (w - borderL - borderR > 0) {
            blit(g, tex, x + borderL, y, w - borderL - borderR, borderT,
                srcBaseX + borderL, srcBaseY, srcCenterW, borderT, texW, texH);
            blit(g, tex, x + borderL, y + h - borderB, w - borderL - borderR, borderB,
                srcBaseX + borderL, srcBaseY + tileH - borderB, srcCenterW, borderB, texW, texH);
        }
        if (h - borderT - borderB > 0) {
            blit(g, tex, x, y + borderT, borderL, h - borderT - borderB,
                srcBaseX, srcBaseY + borderT, borderL, srcCenterH, texW, texH);
            blit(g, tex, x + w - borderR, y + borderT, borderR, h - borderT - borderB,
                srcBaseX + tileW - borderR, srcBaseY + borderT, borderR, srcCenterH, texW, texH);
        }

        // Center
        if (w - borderL - borderR > 0 && h - borderT - borderB > 0) {
            blit(g, tex, x + borderL, y + borderT, w - borderL - borderR, h - borderT - borderB,
                srcBaseX + borderL, srcBaseY + borderT, srcCenterW, srcCenterH, texW, texH);
        }
    }

    private static void blit(GuiGraphics g, ResourceLocation tex,
                              int x, int y, int w, int h,
                              int u, int v, int uW, int vH,
                              int texW, int texH) {
        if (w <= 0 || h <= 0) return;
        g.blit(tex, x, y, w, h, u, v, uW, vH, texW, texH);
    }

    // ── Fluid texture helpers (from AE2/vanilla conventions) ──

    private static void drawFluidTexture(GuiGraphics g, int x, int y, int w, int h, FluidStack stack) {
        if (stack.isEmpty() || stack.getFluid() == null) return;
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTexture = ext.getStillTexture(stack);
        if (stillTexture == null) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        int color = resolveFluidColor(stack);
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float gv = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = ((color >>> 24) & 0xFF) / 255.0F;

        RenderSystem.setShaderColor(r, gv, b, a);
        g.enableScissor(x, y, x + w, y + h);
        for (int ty = y; ty < y + h; ty += 16) {
            for (int tx = x; tx < x + w; tx += 16) {
                g.blit(tx, ty, 0, 16, 16, sprite);
            }
        }
        g.disableScissor();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Resolve a tint color for a fluid stack. Public for Screen-side hover rendering. */
    public static int resolveFluidColor(FluidStack stack, int fallbackColor) {
        if (stack.isEmpty() || stack.getFluid() == null) return fallbackColor;
        if (stack.getFluid() == Fluids.WATER || stack.getFluid() == Fluids.FLOWING_WATER) return 0xFF3A7FD6;
        if (stack.getFluid() == Fluids.LAVA || stack.getFluid() == Fluids.FLOWING_LAVA) return 0xFFFF6A00;
        int tint = IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(stack);
        int rgb = tint & 0x00FFFFFF;
        if (rgb == 0x00FFFFFF || rgb == 0) return fallbackColor;
        return 0xFF000000 | rgb;
    }

    private static int resolveFluidColor(FluidStack stack) {
        return resolveFluidColor(stack, 0xFFFFFFFF);
    }
}
