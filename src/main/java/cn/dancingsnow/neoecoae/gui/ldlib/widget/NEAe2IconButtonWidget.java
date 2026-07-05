package cn.dancingsnow.neoecoae.gui.ldlib.widget;

import cn.dancingsnow.neoecoae.client.gui.ldlib.NELDLibClientStyle;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import net.minecraft.client.gui.GuiGraphics;

public class NEAe2IconButtonWidget extends ButtonWidget {
    private Object icon;
    private IconAlignment iconAlignment;
    private boolean pressed;

    public NEAe2IconButtonWidget(
            int x,
            int y,
            int width,
            int height,
            Object icon,
            java.util.function.Consumer<com.lowdragmc.lowdraglib.gui.util.ClickData> onPress) {
        super(x, y, width, height, IGuiTexture.EMPTY, onPress);
        this.icon = icon;
        this.iconAlignment = IconAlignment.CENTER;
        setHoverTexture(IGuiTexture.EMPTY);
    }

    public NEAe2IconButtonWidget setIcon(Object icon) {
        this.icon = icon;
        return this;
    }

    public NEAe2IconButtonWidget useAeTabButton() {
        this.iconAlignment = IconAlignment.AE_TAB;
        setButtonTexture(IGuiTexture.EMPTY);
        setHoverTexture(IGuiTexture.EMPTY);
        return this;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled && button == 0) {
            pressed = true;
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pressed = false;
        return super.mouseReleased(mouseX, mouseY, button);
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
                graphics, mouseX, mouseY, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(), pressed);
    }

    @Override
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (icon == null) {
            return;
        }
        int offset = pressed ? 1 : 0;
        int iconWidth = NELDLibClientStyle.aeIconWidth(icon);
        int iconHeight = NELDLibClientStyle.aeIconHeight(icon);
        int iconX = iconAlignment == IconAlignment.AE_TAB
                ? getPositionX() + 3 + offset
                : getPositionX() + (getSizeWidth() - iconWidth) / 2 + offset;
        int iconY = iconAlignment == IconAlignment.AE_TAB
                ? getPositionY() + 3 + offset
                : getPositionY() + (getSizeHeight() - iconHeight) / 2 + offset;
        NELDLibClientStyle.drawAeIcon(graphics, icon, iconX, iconY, isActive() ? 1.0F : 0.45F);
    }

    public enum Ae2Icon {
        AUTO_EXPORT_OFF,
        AUTO_EXPORT_ON,
        BACKGROUND_TRASH,
        BACKGROUND_UPGRADE,
        BACKGROUND_WIRELESS_TERM,
        CONDENSER_OUTPUT_TRASH,
        CRAFT_HAMMER,
        FLUID_SUBSTITUTION_DISABLED,
        FLUID_SUBSTITUTION_ENABLED,
        LEVEL_ENERGY,
        POWER_UNIT_AE,
        TYPE_FILTER_ALL,
        WRENCH
    }

    private enum IconAlignment {
        CENTER,
        AE_TAB
    }
}
