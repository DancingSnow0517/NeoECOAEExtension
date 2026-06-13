package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import net.minecraft.client.gui.GuiGraphics;

public class NEAe2IconButtonWidget extends ButtonWidget {
    private Icon icon;
    private IconAlignment iconAlignment;

    public NEAe2IconButtonWidget(
            int x,
            int y,
            int width,
            int height,
            Icon icon,
            java.util.function.Consumer<com.lowdragmc.lowdraglib.gui.util.ClickData> onPress) {
        super(x, y, width, height, IGuiTexture.EMPTY, onPress);
        this.icon = icon;
        this.iconAlignment = IconAlignment.CENTER;
        setHoverTexture(IGuiTexture.EMPTY);
        setClickedTexture(IGuiTexture.EMPTY);
    }

    public NEAe2IconButtonWidget setIcon(Icon icon) {
        this.icon = icon;
        return this;
    }

    public NEAe2IconButtonWidget useAeTabButton() {
        this.iconAlignment = IconAlignment.AE_TAB;
        setButtonTexture(IGuiTexture.EMPTY);
        setHoverTexture(IGuiTexture.EMPTY);
        setClickedTexture(IGuiTexture.EMPTY);
        return this;
    }

    @Override
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        if (iconAlignment == IconAlignment.AE_TAB) {
            NELDLibClientStyle.drawAeTabButton(
                    graphics, mouseX, mouseY, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        } else {
            NELDLibClientStyle.drawAeToolbarButton(
                    graphics, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        }
        NELDLibClientStyle.drawHoverOverlay(
                graphics, mouseX, mouseY, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), isClicked());
    }

    @Override
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (icon == null) {
            return;
        }
        int offset = isClicked() ? 1 : 0;
        int iconX = iconAlignment == IconAlignment.AE_TAB
                ? getPositionX() + 3 + offset
                : getPositionX() + (getSizeWidth() - icon.width) / 2 + offset;
        int iconY = iconAlignment == IconAlignment.AE_TAB
                ? getPositionY() + 3 + offset
                : getPositionY() + (getSizeHeight() - icon.height) / 2 + offset;
        NELDLibAe2StyleRenderer.drawAeIcon(graphics, icon, iconX, iconY, isActive() ? 1.0F : 0.45F);
    }

    private enum IconAlignment {
        CENTER,
        AE_TAB
    }
}
