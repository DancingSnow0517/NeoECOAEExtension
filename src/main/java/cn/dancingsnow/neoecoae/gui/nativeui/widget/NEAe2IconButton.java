package cn.dancingsnow.neoecoae.gui.nativeui.widget;

import appeng.client.gui.Icon;
import cn.dancingsnow.neoecoae.gui.nativeui.screen.NENativeAe2StyleRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * An AE2-style icon button that uses AE2 toolbar button backgrounds
 * and draws an AE2 Icon on top.
 * <p>
 * Typical size: 18×20 (matching AE2 toolbar buttons).
 * Call {@link #setIcon(Icon, Icon)} to set the on/off icons.
 * </p>
 */
public class NEAe2IconButton extends Button {

    private Icon iconOn = Icon.AUTO_EXPORT_ON;
    private Icon iconOff = Icon.AUTO_EXPORT_OFF;
    private boolean toggled;
    private boolean useToggleIcons;

    public NEAe2IconButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
    }

    /** Set the icons for toggle mode. */
    public void setIcons(Icon on, Icon off) {
        this.iconOn = on;
        this.iconOff = off;
        this.useToggleIcons = true;
    }

    /** Set a single icon (non-toggle). */
    public void setIcon(Icon icon) {
        this.iconOn = icon;
        this.iconOff = icon;
        this.useToggleIcons = false;
    }

    public void setToggled(boolean toggled) {
        this.toggled = toggled;
    }

    public boolean isToggled() {
        return toggled;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Use Icon.TOOLBAR_BUTTON_BACKGROUND stretched to button size
        Icon bg = Icon.TOOLBAR_BUTTON_BACKGROUND;
        float alpha = active ? 1.0F : 0.5F;
        if (!active || !isHovered()) {
            // Inactive or not hovered: use at normal/slightly dimmed alpha
        }
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(Icon.TEXTURE, getX(), getY(), width, height,
            bg.x, bg.y, bg.width, bg.height,
            Icon.TEXTURE_WIDTH, Icon.TEXTURE_HEIGHT);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        Icon icon = useToggleIcons ? (toggled ? iconOn : iconOff) : iconOn;
        if (icon != null) {
            int iconX = getX() + (width - icon.width) / 2;
            int iconY = getY() + (height - icon.height) / 2;
            NENativeAe2StyleRenderer.drawAeIcon(g, icon, iconX, iconY, active ? 1.0F : 0.4F);
        }
    }
}
