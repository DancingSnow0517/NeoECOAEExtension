package cn.dancingsnow.neoecoae.gui.widget;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class UploadButton extends Button {
    public static final ResourceLocation ICON_TEXTURE = NeoECOAE.id("textures/gui/upload.png");

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
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int yOffset = isHovered() ? 1 : 0;
        Icon bgIcon = isHovered() ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER
            : isFocused() ? Icon.TOOLBAR_BUTTON_BACKGROUND_FOCUS : Icon.TOOLBAR_BUTTON_BACKGROUND;
        bgIcon.getBlitter()
            .dest(getX() - 1, getY() + yOffset, 18, 20)
            .zOffset(100)
            .blit(guiGraphics);

        guiGraphics.blit(
            ICON_TEXTURE,
            getX(),
            getY() + 2 + yOffset,
            200,
            0,
            0,
            16,
            16,
            16,
            16
        );

        if (isHovered()) {
            guiGraphics.renderComponentTooltip(
                Minecraft.getInstance().font,
                List.of(
                    Component.translatable("neoecoae.tooltip.upload_pattern")
                ),
                mouseX,
                mouseY
            );
        }
    }
}
