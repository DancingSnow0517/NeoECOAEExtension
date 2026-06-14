package cn.dancingsnow.neoecoae.gui.widget;

import appeng.client.gui.style.Blitter;
import appeng.util.Icon;
import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class UploadButton extends Button {
    public static final Identifier ICON_TEXTURE = NeoECOAE.id("textures/gui/upload.png");

    public UploadButton(int x, int y, OnPress onPress) {
        super(
            x,
            y,
            18,
            20,
            Component.empty(),
            onPress,
            unused -> Component.empty()
        );
        setTooltip(Tooltip.create(Component.translatable("neoecoae.tooltip.upload_pattern")));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int yOffset = isHovered() ? 1 : 0;
        Icon bgIcon = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;
        Blitter.icon(bgIcon)
            .dest(getX() - 1, getY() + yOffset, 18, 20)
            .blit(guiGraphics);

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, ICON_TEXTURE, getX(), getY() + 2 + yOffset, 0, 0, 16, 16, 16, 16);
    }
}
