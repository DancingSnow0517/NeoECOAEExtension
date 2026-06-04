package cn.dancingsnow.neoecoae.gui.nativeui.screen;

import appeng.client.gui.Icon;
import appeng.client.gui.style.BackgroundGenerator;
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
 * All drawing methods reference AE2 namespace resources (textures, Icon enum,
 * BackgroundGenerator) so that machine GUIs follow AE2 resource-pack changes
 * automatically. No project GUI textures are used here.
 * </p>
 */
public final class NENativeAe2StyleRenderer {

    private NENativeAe2StyleRenderer() {}

    // ── AE2 common resources ──

    /** AE2 extra_panels.png — upgrade panel atlas. */
    public static final ResourceLocation AE_EXTRA_PANELS =
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/extra_panels.png");

    public static final int EXTRA_TEX_W = 128;
    public static final int EXTRA_TEX_H = 128;

    /** AE2 inscriber.png — progress bar texture. */
    private static final ResourceLocation AE_INSCRIBER =
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/inscriber.png");

    private static final int INSCRIBER_PROGRESS_U = 135;
    private static final int INSCRIBER_PROGRESS_V = 177;
    private static final int INSCRIBER_PROGRESS_W = 6;
    private static final int INSCRIBER_PROGRESS_H = 18;
    private static final int INSCRIBER_TEX_W = 256;
    private static final int INSCRIBER_TEX_H = 256;

    public static final int UPGRADE_PADDING = 7;
    public static final int SLOT_SIZE = 18;

    // ── Panel drawing ──

    /**
     * Draw an AE2-style main panel using AE2's own BackgroundGenerator.
     * This produces the same generated background as every AE2 screen.
     */
    public static void drawAeMainPanel(GuiGraphics g, int x, int y, int w, int h) {
        BackgroundGenerator.draw(w, h, g, x, y);
    }

    /**
     * Draw a very subtle AE2-style slot-group inset — barely visible,
     * just a 1px dark border without white highlight or fill.
     * Use sparingly; most slot groups should just use the main
     * BackgroundGenerator panel + Icon.SLOT_BACKGROUND alone.
     */
    public static void drawAeSlotGroupInset(GuiGraphics g, int x, int y, int w, int h) {
        // Single-pixel dark border only — no fill, no white highlight
        g.fill(x, y, x + w, y + 1, 0xFF555555);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF555555);
        g.fill(x, y, x + 1, y + h, 0xFF555555);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF555555);
    }

    // ── Toolbar / button ──

    /** Draw an AE2-style toolbar button background using the official Icon. */
    public static void drawAeToolbarButtonBackground(
            GuiGraphics g, int x, int y, int w, int h, boolean hovered, boolean active) {
        Icon icon = active
                ? (hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND : Icon.TOOLBAR_BUTTON_BACKGROUND)
                : Icon.TOOLBAR_BUTTON_BACKGROUND;
        // Stretch the 16×16 toolbar button icon to fill the requested size
        RenderSystem.enableBlend();
        if (!active) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.5F);
        }
        g.blit(
                Icon.TEXTURE,
                x,
                y,
                w,
                h,
                icon.x,
                icon.y,
                icon.width,
                icon.height,
                Icon.TEXTURE_WIDTH,
                Icon.TEXTURE_HEIGHT);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ── AE2 IO Port background — baked slot patch ──

    /** AE2 IO Port background texture, used as the source for baked player slots. */
    private static final ResourceLocation AE_IO_PORT =
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/io_port.png");

    private static final int AE_MACHINE_TEX_W = 256;
    private static final int AE_MACHINE_TEX_H = 256;

    // AE2 IO Port background: 176×166
    // common/player_inventory.json: PLAYER_INVENTORY slot left=8, bottom=82
    // slotY = 166 - 82 = 84  →  bgY = 83
    private static final int AE_BAKED_SLOT_U = 7;
    private static final int AE_BAKED_SLOT_V = 83;
    private static final int AE_BAKED_SLOT_SIZE = 18;

    // ── Slots ──

    /**
     * Draw a player-style slot from the AE2 IO Port baked background.
     * This matches the colour of AE2 machine GUIs exactly.
     */
    public static void drawAeSlot(GuiGraphics g, int x, int y) {
        g.blit(
                AE_IO_PORT,
                x,
                y,
                AE_BAKED_SLOT_U,
                AE_BAKED_SLOT_V,
                AE_BAKED_SLOT_SIZE,
                AE_BAKED_SLOT_SIZE,
                AE_MACHINE_TEX_W,
                AE_MACHINE_TEX_H);
    }

    // ── Icons ──

    /** Draw an AE2 Icon at (x, y) with given alpha. */
    public static void drawAeIcon(GuiGraphics g, Icon icon, int x, int y, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(Icon.TEXTURE, x, y, icon.x, icon.y, icon.width, icon.height, Icon.TEXTURE_WIDTH, Icon.TEXTURE_HEIGHT);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Draw an AE2 Icon at (x, y) with full opacity. */
    public static void drawAeIcon(GuiGraphics g, Icon icon, int x, int y) {
        drawAeIcon(g, icon, x, y, 1.0F);
    }

    // ── Upgrade panel ──

    /**
     * Draw an AE2-style upgrade panel on the right side.
     * Uses extra_panels.png, matching AE2's UpgradesPanel layout.
     *
     * @param g         graphics context
     * @param panelX    left edge of the upgrade panel outer frame
     * @param panelY    top edge of the upgrade panel outer frame
     * @param slotCount number of upgrade slots
     */
    public static void drawAeUpgradePanel(GuiGraphics g, int panelX, int panelY, int slotCount) {
        final int PADDING = UPGRADE_PADDING;

        for (int i = 0; i < slotCount; i++) {
            int srcX = PADDING;
            int srcY = PADDING;
            int srcW = SLOT_SIZE;
            int srcH = SLOT_SIZE;
            // drawX/drawY = the blit destination top-left
            int drawX = panelX + PADDING; // slot visual starts at panelX + PADDING
            int drawY = panelY + PADDING + i * SLOT_SIZE;

            boolean borderLeft = true;
            boolean borderRight = true;
            boolean borderTop = i == 0;
            boolean borderBottom = i == slotCount - 1;

            if (borderLeft) {
                drawX = panelX; // extend to panel left edge
                srcX = 0;
                srcW += PADDING;
            }
            if (borderRight) {
                srcW += PADDING;
            }
            if (borderTop) {
                drawY = panelY; // extend to panel top edge
                srcY = 0;
                srcH += PADDING;
            }
            if (borderBottom) {
                srcH += PADDING;
            }

            g.blit(AE_EXTRA_PANELS, drawX, drawY, srcX, srcY, srcW, srcH, EXTRA_TEX_W, EXTRA_TEX_H);
        }
    }

    // ── Inset rectangle ──

    /**
     * Draw an inset (sunken) rectangle with AE2-style shadow/highlight.
     * Used for fluid tanks and progress bars to match the recessed look.
     *
     * @param fillColor the interior fill color
     */
    public static void drawAeInsetRect(GuiGraphics g, int x, int y, int w, int h, int fillColor) {
        // top/left shadow
        g.fill(x, y, x + w, y + 1, 0xFF3F3F3F);
        g.fill(x, y, x + 1, y + h, 0xFF3F3F3F);

        // bottom/right highlight
        g.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);

        // inner top/left shadow
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF707070);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF707070);

        // inner fill
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, fillColor);
    }

    // ── Fluid tank ──

    /**
     * Draw a fluid tank with recessed AE2-style inset border and fluid texture tiling.
     */
    public static void drawAeFluidTank(
            GuiGraphics g, int x, int y, int w, int h, FluidStack stack, int amount, int capacity) {
        drawAeInsetRect(g, x, y, w, h, 0xFF8E8E8E);

        int ix = x + 2;
        int iy = y + 2;
        int iw = w - 4;
        int ih = h - 4;

        if (amount <= 0 || stack.isEmpty() || capacity <= 0) {
            return;
        }

        int barH = Mth.clamp((int) ((long) amount * ih / capacity), 1, ih);
        int fillY = iy + ih - barH;
        drawFluidTexture(g, ix, fillY, iw, barH, stack);
    }

    // ── Output frame (Inscriber-style large frame) ──

    /**
     * Draw an AE2 Inscriber-style output frame — a larger recessed frame
     * that visually contains the output item slot (which remains a 16×16
     * Menu Slot, drawn separately).
     */
    public static void drawAeInscriberOutputFrame(GuiGraphics g, int x, int y, int w, int h) {
        // outer top/left shadow
        g.fill(x, y, x + w, y + 1, 0xFF3F3F3F);
        g.fill(x, y, x + 1, y + h, 0xFF3F3F3F);

        // outer bottom/right highlight
        g.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);

        // inner fill
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF9B9B9B);

        // subtle inner darkening
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x10000000);
    }

    // ── Progress bar ──

    /**
     * Draw a progress bar using the AE2 inscriber.png texture.
     * Empty background is a recessed inset rect; the filled portion
     * uses the actual AE2 inscriber progress bar sprite.
     */
    public static void drawAeProgressBar(GuiGraphics g, int x, int y, int w, int h, int progress, int maxProgress) {
        // draw a recessed empty background first
        drawAeInsetRect(g, x, y, w, h, 0xFF8E8E8E);

        if (maxProgress <= 0 || progress <= 0) {
            return;
        }

        int fullH = INSCRIBER_PROGRESS_H;
        int fillH = Mth.clamp((int) ((long) progress * fullH / maxProgress), 1, fullH);

        // Fill from bottom to top, matching the current vertical bar behavior.
        int srcY = INSCRIBER_PROGRESS_V + fullH - fillH;
        int dstY = y + (h - fullH) / 2 + fullH - fillH;

        int dstX = x + (w - INSCRIBER_PROGRESS_W) / 2;

        g.blit(
                AE_INSCRIBER,
                dstX,
                dstY,
                INSCRIBER_PROGRESS_U,
                srcY,
                INSCRIBER_PROGRESS_W,
                fillH,
                INSCRIBER_TEX_W,
                INSCRIBER_TEX_H);
    }

    // ── Fluid texture helpers ──

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

    private static void drawFluidTexture(GuiGraphics g, int x, int y, int w, int h, FluidStack stack) {
        if (stack.isEmpty() || stack.getFluid() == null) return;
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTexture = ext.getStillTexture(stack);
        if (stillTexture == null) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(stillTexture);
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
}
