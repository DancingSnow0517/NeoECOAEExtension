package cn.dancingsnow.neoecoae.gui.widget;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.TabButton;
import cn.dancingsnow.neoecoae.NeoECOAE;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Upload action drawn as one more AE2 encoding-mode tab, so background, focus and tooltip follow AE2's {@link TabButton}.
 */
public class UploadTabButton extends TabButton {
    public static final ResourceLocation ICON_TEXTURE = NeoECOAE.id("textures/gui/widget/eco_uploading_icon.png");

    public UploadTabButton(OnPress onPress) {
        super((Icon) null, Component.translatable("neoecoae.tooltip.upload_pattern"), onPress);
        setStyle(Style.HORIZONTAL);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        if (visible) {
            // Same inset AE2 uses for HORIZONTAL tab icons (3, 3 - 1).
            guiGraphics.blit(ICON_TEXTURE, getX() + 3, getY() + 2, 0, 0, 16, 16, 16, 16);
        }
    }
}
