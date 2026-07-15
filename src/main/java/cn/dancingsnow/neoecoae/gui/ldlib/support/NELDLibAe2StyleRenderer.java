package cn.dancingsnow.neoecoae.gui.ldlib.support;

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

public final class NELDLibAe2StyleRenderer {
    private static final ResourceLocation NE_SLOT =
            ResourceLocation.fromNamespaceAndPath("neoecoae", "textures/gui/slot.png");
    private static final ResourceLocation AE_EXTRA_PANELS =
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/extra_panels.png");
    private static final ResourceLocation AE_INSCRIBER =
            ResourceLocation.fromNamespaceAndPath("ae2", "textures/guis/inscriber.png");

    private static final int EXTRA_TEX_W = 128;
    private static final int EXTRA_TEX_H = 128;
    private static final int INSCRIBER_PROGRESS_U = 135;
    private static final int INSCRIBER_PROGRESS_V = 177;
    private static final int INSCRIBER_PROGRESS_W = 6;
    private static final int INSCRIBER_PROGRESS_H = 18;
    private static final int INSCRIBER_TEX_W = 256;
    private static final int INSCRIBER_TEX_H = 256;
    private static final int UPGRADE_PADDING = 7;
    private static final int SLOT_SIZE = 18;

    private NELDLibAe2StyleRenderer() {}

    public static void drawAeMainPanel(GuiGraphics g, int x, int y, int w, int h) {
        BackgroundGenerator.draw(w, h, g, x, y);
    }

    public static void drawAeSlot(GuiGraphics g, int x, int y) {
        g.blit(NE_SLOT, x, y, 0, 0, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
    }

    public static void drawAeIcon(GuiGraphics g, Icon icon, int x, int y, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(Icon.TEXTURE, x, y, icon.x, icon.y, icon.width, icon.height, Icon.TEXTURE_WIDTH, Icon.TEXTURE_HEIGHT);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawAeIcon(GuiGraphics g, Icon icon, int x, int y) {
        drawAeIcon(g, icon, x, y, 1.0F);
    }

    public static void drawAeUpgradePanel(GuiGraphics g, int panelX, int panelY, int slotCount) {
        for (int i = 0; i < slotCount; i++) {
            int srcX = UPGRADE_PADDING;
            int srcY = UPGRADE_PADDING;
            int srcW = SLOT_SIZE;
            int srcH = SLOT_SIZE;
            int drawX = panelX + UPGRADE_PADDING;
            int drawY = panelY + UPGRADE_PADDING + i * SLOT_SIZE;

            if (i == 0) {
                drawY = panelY;
                srcY = 0;
                srcH += UPGRADE_PADDING;
            }
            if (i == slotCount - 1) {
                srcH += UPGRADE_PADDING;
            }

            drawX = panelX;
            srcX = 0;
            srcW += UPGRADE_PADDING * 2;

            g.blit(AE_EXTRA_PANELS, drawX, drawY, srcX, srcY, srcW, srcH, EXTRA_TEX_W, EXTRA_TEX_H);
        }
    }

    public static void drawAeInsetRect(GuiGraphics g, int x, int y, int w, int h, int fillColor) {
        g.fill(x, y, x + w, y + 1, 0xFF3F3F3F);
        g.fill(x, y, x + 1, y + h, 0xFF3F3F3F);
        g.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF707070);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF707070);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, fillColor);
    }

    public static void drawAeInscriberOutputFrame(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 1, 0xFF3F3F3F);
        g.fill(x, y, x + 1, y + h, 0xFF3F3F3F);
        g.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF9B9B9B);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, 0x10000000);
    }

    public static void drawAeProgressBar(GuiGraphics g, int x, int y, int w, int h, int progress, int maxProgress) {
        drawAeInsetRect(g, x, y, w, h, 0xFF8E8E8E);
        if (maxProgress <= 0 || progress <= 0) {
            return;
        }

        int fillH = Mth.clamp((int) ((long) progress * INSCRIBER_PROGRESS_H / maxProgress), 1, INSCRIBER_PROGRESS_H);
        int srcY = INSCRIBER_PROGRESS_V + INSCRIBER_PROGRESS_H - fillH;
        int dstY = y + (h - INSCRIBER_PROGRESS_H) / 2 + INSCRIBER_PROGRESS_H - fillH;
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

    public static int resolveFluidColor(FluidStack stack, int fallbackColor) {
        if (stack.isEmpty() || stack.getFluid() == null) return fallbackColor;
        if (stack.getFluid() == Fluids.WATER || stack.getFluid() == Fluids.FLOWING_WATER) return 0xFF3A7FD6;
        if (stack.getFluid() == Fluids.LAVA || stack.getFluid() == Fluids.FLOWING_LAVA) return 0xFFFF6A00;
        int tint = IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(stack);
        int rgb = tint & 0x00FFFFFF;
        if (rgb == 0x00FFFFFF || rgb == 0) return fallbackColor;
        return 0xFF000000 | rgb;
    }

    public static void drawAeFluidTankSimple(
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
        g.enableScissor(ix, fillY, ix + iw, iy + ih);
        drawFluidTextureFull(g, ix, iy, iw, ih, stack, iy + ih);
        g.disableScissor();
    }

    public static void drawFluidIcon(GuiGraphics g, int x, int y, int size, FluidStack stack) {
        if (size <= 0 || stack.isEmpty()) {
            return;
        }
        drawFluidTextureFull(g, x, y, size, size, stack, y + size);
    }

    public static void drawFluidFill(
            GuiGraphics g, int x, int y, int width, int height, FluidStack stack, long amount, long capacity) {
        if (width <= 0 || height <= 0 || amount <= 0L || capacity <= 0L || stack.isEmpty()) {
            return;
        }
        int fillHeight = Mth.clamp((int) (Math.min(amount, capacity) * height / capacity), 1, height);
        int fillY = y + height - fillHeight;
        g.enableScissor(x, fillY, x + width, y + height);
        drawFluidTextureFull(g, x, y, width, height, stack, y + height);
        g.disableScissor();
    }

    private static void drawFluidTextureFull(
            GuiGraphics g, int x, int topY, int w, int h, FluidStack stack, int bottomY) {
        if (stack.isEmpty() || stack.getFluid() == null) return;
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(stack.getFluid());
        ResourceLocation stillTexture = ext.getStillTexture(stack);
        if (stillTexture == null) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(stillTexture);
        int color = resolveFluidColor(stack, 0xFFFFFFFF);
        float r = ((color >>> 16) & 0xFF) / 255.0F;
        float gv = ((color >>> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = ((color >>> 24) & 0xFF) / 255.0F;

        RenderSystem.setShaderColor(r, gv, b, a);
        for (int ty = topY; ty < bottomY; ty += 16) {
            for (int tx = x; tx < x + w; tx += 16) {
                g.blit(tx, ty, 0, 16, 16, sprite);
            }
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
