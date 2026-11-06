package cn.dancingsnow.neoecoae.gui.widget;

import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class UploadButton extends Button {
    public static final ResourceLocation ICON_TEXTURE = NeoECOAE.id("textures/gui/widget/upload.png");

    public UploadButton(int x, int y, OnPress onPress) {
        super(
            x,
            y,
            18,
            20,
            Component.translatable("neoecoae.tooltip.upload_pattern"),
            onPress,
            Button.DEFAULT_NARRATION
        );
        setTooltip(Tooltip.create(getMessage()));
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        ECOIcon bgIcon = isHovered() ? ECOIcon.TOOLBAR_BUTTON_BACKGROUND_HOVER
            : isFocused() ? ECOIcon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : ECOIcon.TOOLBAR_BUTTON_BACKGROUND;
        bgIcon.blit(guiGraphics, getX(), getY(), 100);

        guiGraphics.blit(
            ICON_TEXTURE,
            getX() + 1,
            getY() + 2,
            200,
            0,
            0,
            16,
            16,
            16,
            16
        );
    }
}
