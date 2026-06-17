package cn.dancingsnow.neoecoae.gui.host;

import appeng.core.AppEng;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public abstract class NEAeWidgetCanvas extends UIElement {
    static final ResourceLocation PRIORITY_BACKGROUND = AppEng.makeId("textures/guis/priority.png");
    private static final ResourceLocation TEXT_FIELD = AppEng.makeId("textures/guis/text_field.png");
    private static final ResourceLocation BUTTON = AppEng.makeId("button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = AppEng.makeId("button_highlighted");
    private static final int TEXT_DARK = 0xFF3F3D52;
    private static final int TEXT_BUTTON = 0xFFF2F2F2;
    private static final int TEXT_BUTTON_HOVER = 0xFF517497;

    private final int width;
    private final int height;

    protected NEAeWidgetCanvas(int width, int height) {
        this.width = width;
        this.height = height;
        layout(layout -> layout.width(width).height(height));
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        drawWidget(context);
    }

    protected abstract void drawWidget(GUIContext context);

    protected void drawPriorityBackground(GUIContext context) {
        context.graphics.blit(PRIORITY_BACKGROUND, ix(0), iy(0), 0, 0, width, height, 256, 256);
    }

    protected void drawAeButton(GUIContext context, int x, int y, int w, int h, Component text, boolean hovered) {
        context.graphics.blitSprite(hovered ? BUTTON_HIGHLIGHTED : BUTTON, ix(x), iy(y), w, h);
        int color = hovered ? TEXT_BUTTON_HOVER : TEXT_BUTTON;
        int yOffset = hovered ? 0 : 1;
        int textX = ix(x) + (w - context.mc.font.width(text)) / 2;
        int textY = iy(y) + (h - 9) / 2 - yOffset + 1;
        context.graphics.drawString(context.mc.font, text, textX, textY, color, false);
    }

    protected void drawPriorityValueSlot(GUIContext context, int x, int y, int w, int h, boolean focused) {
        int ax = ix(x);
        int ay = iy(y);
        int middleWidth = Math.max(0, w - 2);
        context.graphics.blit(TEXT_FIELD, ax, ay, 0, 0, 1, h, 128, 128);
        context.graphics.blit(TEXT_FIELD, ax + 1, ay, 1, 0, middleWidth, h, 128, 128);
        context.graphics.blit(TEXT_FIELD, ax + w - 1, ay, 127, 0, 1, h, 128, 128);
    }

    protected void drawToolbarIconButton(GUIContext context, int x, int y, NEAeSprite icon, boolean hovered) {
        int yOffset = hovered ? 1 : 0;
        NEAeSprite bg = hovered ? NEAeSprite.TOOLBAR_BUTTON_BACKGROUND_HOVER : NEAeSprite.TOOLBAR_BUTTON_BACKGROUND;
        bg.draw(context, ix(x - 1), iy(y + yOffset), 18, 20);
        icon.draw(context, ix(x), iy(y + 1 + yOffset));
    }

    protected void drawTabIconButton(GUIContext context, int x, int y, NEAeSprite icon, boolean focused) {
        NEAeSprite bg = focused ? NEAeSprite.TAB_BUTTON_BACKGROUND_FOCUS : NEAeSprite.TAB_BUTTON_BACKGROUND;
        bg.draw(context, ix(x), iy(y));
        icon.draw(context, ix(x + 2), iy(y + 1));
    }

    protected void drawText(GUIContext context, Component text, int x, int y, int color) {
        context.graphics.drawString(context.mc.font, text, ix(x), iy(y), color, false);
    }

    protected void drawDarkText(GUIContext context, Component text, int x, int y) {
        drawText(context, text, x, y, TEXT_DARK);
    }

    protected int ix(int x) {
        return Math.round(getPositionX()) + x;
    }

    protected int iy(int y) {
        return Math.round(getPositionY()) + y;
    }

    protected boolean hovered(GUIContext context, int x, int y, int w, int h) {
        double mouseX = context.mouseX;
        double mouseY = context.mouseY;
        return mouseX >= ix(x) && mouseX < ix(x) + w && mouseY >= iy(y) && mouseY < iy(y) + h;
    }
}
