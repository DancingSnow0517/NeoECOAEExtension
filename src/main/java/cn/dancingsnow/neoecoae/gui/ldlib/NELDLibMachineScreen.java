package cn.dancingsnow.neoecoae.gui.ldlib;

import cn.dancingsnow.neoecoae.gui.nativeui.NENativeUiConstants;
import cn.dancingsnow.neoecoae.gui.nativeui.menu.NEBaseMachineMenu;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectAndBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ProgressWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.Position;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Small client-side wrapper for LDLib1 widgets hosted by the existing native
 * menus. The menu and packet layer remain the authoritative state source.
 */
public abstract class NELDLibMachineScreen<T extends NEBaseMachineMenu> extends AbstractContainerScreen<T> {
    protected static final int TEXT_PRIMARY = 0xFF404040;
    protected static final int TEXT_MUTED = 0xFF707070;
    protected static final int TEXT_VALUE = 0xFF315F92;
    protected static final int TEXT_SUCCESS = 0xFF1A7A3A;
    protected static final int TEXT_WARNING = 0xFF8A5A1A;
    protected static final int TEXT_ERROR = 0xFF9A2A3A;

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final IGuiTexture BACKGROUND =
            new GuiTextureGroup(new ColorRectTexture(0xFFE8E8E8), ResourceBorderTexture.BORDERED_BACKGROUND.copy());

    private final List<Widget> ldWidgets = new ArrayList<>();

    protected NELDLibMachineScreen(T menu, Inventory playerInv, Component title, int imageWidth, int imageHeight) {
        super(menu, playerInv, title);
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }

    @Override
    protected void init() {
        super.init();
        ldWidgets.clear();
        if (shouldAddTitleWidget()) {
            addText(
                    NENativeUiConstants.TITLE_X,
                    NENativeUiConstants.TITLE_Y,
                    imageWidth - 16,
                    9,
                    () -> title,
                    TEXT_PRIMARY,
                    TextTexture.TextType.LEFT_HIDE);
        }
        initLdWidgets();
    }

    protected boolean shouldAddTitleWidget() {
        return true;
    }

    protected abstract void initLdWidgets();

    protected <W extends Widget> W addLdWidget(W widget) {
        widget.setClientSideWidget();
        widget.setParentPosition(new Position(leftPos, topPos));
        widget.initWidget();
        ldWidgets.add(widget);
        return widget;
    }

    protected TextTextureWidget addText(
            int x, int y, int w, int h, Supplier<Component> text, int color, TextTexture.TextType type) {
        TextTextureWidget widget = new TextTextureWidget(x, y, w, h).setText(text);
        widget.textureStyle(
                texture -> texture.setColor(color).setDropShadow(false).setType(type));
        return addLdWidget(widget);
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
        return addLdWidget(widget);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        for (Widget widget : ldWidgets) {
            widget.updateScreen();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderLdForeground(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
        renderLdTooltips(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        BACKGROUND.draw(guiGraphics, mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight);
        renderLdBackground(guiGraphics, mouseX, mouseY, partialTick);
        for (Widget widget : ldWidgets) {
            widget.drawInBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    protected void renderLdBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}

    protected void renderLdForeground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}

    protected void renderLdTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {}

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = ldWidgets.size() - 1; i >= 0; i--) {
            Widget widget = ldWidgets.get(i);
            if (widget.isVisible() && widget.isActive() && widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (int i = ldWidgets.size() - 1; i >= 0; i--) {
            Widget widget = ldWidgets.get(i);
            if (widget.isVisible() && widget.isActive() && widget.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    protected void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        int left = leftPos + x;
        int top = topPos + y;
        g.fill(left, top, left + w, top + h, 0xFFC6CAD4);
        g.fill(left + 1, top + 1, left + w - 1, top + h - 1, 0xFFF5F6F8);
        g.fill(left + 2, top + 2, left + w - 2, top + h - 2, 0xFFE2E5EA);
    }

    protected void drawLocalString(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font, text, leftPos + x, topPos + y, color, false);
    }

    protected void drawCenteredLocalString(GuiGraphics g, Component text, int x, int y, int w, int color) {
        g.drawString(font, text, leftPos + x + (w - font.width(text)) / 2, topPos + y, color, false);
    }

    protected boolean isMouseIn(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + w && mouseY >= topPos + y && mouseY < topPos + y + h;
    }

    protected Component boolText(boolean value) {
        return Component.translatable(value ? "gui.neoecoae.common.yes" : "gui.neoecoae.common.no");
    }

    protected static String fmt(long value) {
        return NUMBER_FORMAT.format(value);
    }

    protected static double percent(long used, long max) {
        if (max <= 0) {
            return 0.0D;
        }
        return Mth.clamp((double) used / (double) max, 0.0D, 1.0D);
    }
}
