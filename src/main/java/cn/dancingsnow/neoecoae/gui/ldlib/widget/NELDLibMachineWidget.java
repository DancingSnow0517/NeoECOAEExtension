package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibText;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibUiConstants;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectAndBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public abstract class NELDLibMachineWidget extends WidgetGroup {
    protected static final int TEXT_PRIMARY = NELDLibUiConstants.TEXT_PRIMARY;
    protected static final int TEXT_MUTED = NELDLibUiConstants.TEXT_MUTED;
    protected static final int TEXT_VALUE = NELDLibUiConstants.TEXT_VALUE;
    protected static final int TEXT_SUCCESS = NELDLibUiConstants.TEXT_SUCCESS;
    protected static final int TEXT_WARNING = NELDLibUiConstants.TEXT_WARNING;
    protected static final int TEXT_ERROR = NELDLibUiConstants.TEXT_ERROR;

    protected final Component title;
    protected final int width;
    protected final int height;

    protected NELDLibMachineWidget(Component title, int width, int height) {
        super(0, 0, width, height);
        this.title = title;
        this.width = width;
        this.height = height;
        setBackground(IGuiTexture.EMPTY);
    }

    @Override
    public void initWidget() {
        clearAllWidgets();
        if (shouldAddTitleWidget()) {
            addText(
                    NELDLibUiConstants.TITLE_X,
                    NELDLibUiConstants.TITLE_Y,
                    width - 16,
                    9,
                    () -> title,
                    TEXT_PRIMARY,
                    TextTexture.TextType.LEFT_HIDE);
        }
        initLdWidgets();
        super.initWidget();
    }

    protected boolean shouldAddTitleWidget() {
        return true;
    }

    protected abstract void initLdWidgets();

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (shouldDrawBasePanel()) {
            NELDLibAe2StyleRenderer.drawAeMainPanel(graphics, getPositionX(), getPositionY(), width, height);
        }
        drawMachineBackground(graphics, mouseX, mouseY, partialTicks);
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    protected boolean shouldDrawBasePanel() {
        return true;
    }

    @Override
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        drawMachineForeground(graphics, mouseX, mouseY, partialTicks);
        drawMachineTooltips(graphics, mouseX, mouseY);
    }

    protected void drawMachineBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {}

    protected void drawMachineForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {}

    protected void drawMachineTooltips(GuiGraphics graphics, int mouseX, int mouseY) {}

    protected TextTextureWidget addText(
            int x, int y, int w, int h, Supplier<Component> text, int color, TextTexture.TextType type) {
        TextTextureWidget widget = new TextTextureWidget(x, y, w, h).setText(text);
        widget.textureStyle(
                texture -> texture.setColor(color).setDropShadow(false).setType(type));
        addWidget(widget);
        return widget;
    }

    protected ProgressWidget addProgress(
            int x,
            int y,
            int w,
            int h,
            Supplier<Double> percent,
            int fillColor,
            ProgressTexture.FillDirection direction) {
        ProgressWidget widget = new ProgressWidget(() -> Mth.clamp(percent.get(), 0.0D, 1.0D), x, y, w, h)
                .setProgressTexture(new ColorRectTexture(0xFF242631), new ColorRectTexture(fillColor))
                .setFillDirection(direction)
                .setOverlayTexture(new ColorRectAndBorderTexture(0x00000000, 0xFF9AA0AA, 1.0F));
        addWidget(widget);
        return widget;
    }

    protected void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        int left = absX(x);
        int top = absY(y);
        g.fill(left, top, left + w, top + h, 0xFFC6CAD4);
        g.fill(left + 1, top + 1, left + w - 1, top + h - 1, 0xFFF5F6F8);
        g.fill(left + 2, top + 2, left + w - 2, top + h - 2, 0xFFE2E5EA);
    }

    protected void drawLocalString(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font(), text, absX(x), absY(y), color, false);
    }

    protected void drawCenteredLocalString(GuiGraphics g, Component text, int x, int y, int w, int color) {
        Font font = font();
        g.drawString(font, text, absX(x) + (w - font.width(text)) / 2, absY(y), color, false);
    }

    protected void drawRightLocalString(GuiGraphics g, Component text, int rightX, int y, int color) {
        Font font = font();
        g.drawString(font, text, absX(rightX) - font.width(text), absY(y), color, false);
    }

    protected boolean isMouseIn(int x, int y, int w, int h, int mouseX, int mouseY) {
        return Widget.isMouseOver(absX(x), absY(y), w, h, mouseX, mouseY);
    }

    protected Component boolText(boolean value) {
        return Component.translatable(value ? "gui.neoecoae.common.yes" : "gui.neoecoae.common.no");
    }

    protected int absX(int localX) {
        return getPositionX() + localX;
    }

    protected int absY(int localY) {
        return getPositionY() + localY;
    }

    protected Font font() {
        return Minecraft.getInstance().font;
    }

    protected static String fmt(long value) {
        return NELDLibText.number(value);
    }

    protected static double percent(long used, long max) {
        if (max <= 0) {
            return 0.0D;
        }
        return Mth.clamp((double) used / (double) max, 0.0D, 1.0D);
    }
}
