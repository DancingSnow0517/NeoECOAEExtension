package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibAe2StyleRenderer;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NELDLibStyle;
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
        super(x, y, width, height, NELDLibStyle.aeToolbarButton(), onPress);
        this.icon = icon;
        this.iconAlignment = IconAlignment.CENTER;
        setHoverTexture((IGuiTexture) (graphics, mouseX, mouseY, drawX, drawY, drawWidth, drawHeight) -> graphics.fill(
                Math.round(drawX) + 1,
                Math.round(drawY) + 1,
                Math.round(drawX) + drawWidth - 1,
                Math.round(drawY) + drawHeight - 1,
                0x28FFFFFF));
        setClickedTexture((IGuiTexture) (graphics, mouseX, mouseY, drawX, drawY, drawWidth, drawHeight) -> {
            NELDLibStyle.aeToolbarButton().draw(graphics, mouseX, mouseY, drawX, drawY, drawWidth, drawHeight);
            graphics.fill(
                    Math.round(drawX) + 1,
                    Math.round(drawY) + 1,
                    Math.round(drawX) + drawWidth - 1,
                    Math.round(drawY) + drawHeight - 1,
                    0x38000000);
        });
    }

    public NEAe2IconButtonWidget setIcon(Icon icon) {
        this.icon = icon;
        return this;
    }

    public NEAe2IconButtonWidget useAeTabButton() {
        this.iconAlignment = IconAlignment.AE_TAB;
        setButtonTexture(NELDLibStyle.aeTabButton());
        setHoverTexture(IGuiTexture.EMPTY);
        setClickedTexture((IGuiTexture) (graphics, mouseX, mouseY, drawX, drawY, drawWidth, drawHeight) -> {
            NELDLibStyle.aeTabButton().draw(graphics, mouseX, mouseY, drawX, drawY, drawWidth, drawHeight);
            graphics.fill(
                    Math.round(drawX) + 1,
                    Math.round(drawY) + 1,
                    Math.round(drawX) + drawWidth - 1,
                    Math.round(drawY) + drawHeight - 1,
                    0x22000000);
        });
        return this;
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
