package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.gui.theme.AETextures;
import cn.dancingsnow.neoecoae.gui.theme.ECOIcon;
import cn.dancingsnow.neoecoae.gui.theme.NETextures;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.texture.IGuiTexture;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.Button;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.elements.TextField;
import cn.dancingsnow.neoecoae.platform.ui.runtime.gui.ui.rendering.GUIContext;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Shared AE2 artwork and drawing rules for the priority and builder subwindows. */
public final class HostSubWindowControls {
    private HostSubWindowControls() {}

    public static Button textButton(Supplier<Component> label) {
        return new TextButton(label);
    }

    public static Button backButton(Runnable close) {
        return new BackButton().setOnClick(event -> close.run());
    }

    public static TextField integerField(int min, int max) {
        return new IntegerField().setNumbersOnlyInt(min, max)
            .textFieldStyle(style -> style.textColor(0xf2f2f2).textShadow(false)
                .focusOverlay(IGuiTexture.EMPTY).placeholder(Component.literal(String.valueOf(min))));
    }

    private static final class TextButton extends Button {
        private final Supplier<Component> label;

        private TextButton(Supplier<Component> label) {
            this.label = label;
            noText();
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            IGuiTexture texture = !isActive() ? NETextures.AE2_BUTTON_DISABLED
                : getState() == State.DEFAULT ? NETextures.AE2_BUTTON : NETextures.AE2_BUTTON_HIGHLIGHTED;
            context.drawTexture(texture, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        }

        @Override
        public void drawContents(GUIContext context) {
            super.drawContents(context);
            Font font = Minecraft.getInstance().font;
            Component value = label.get();
            int x = Math.round(getPositionX());
            int y = Math.round(getPositionY());
            int width = Math.round(getSizeWidth());
            int height = Math.round(getSizeHeight());
            int offset = !isActive() ? -1 : getState() == State.DEFAULT ? 1 : 0;
            int color = !isActive() ? 0xFF413F54 : getState() == State.DEFAULT ? 0xFFF2F2F2 : 0xFF517497;
            context.graphics.drawString(font, value, x + width / 2 - font.width(value) / 2,
                y + (height - 9) / 2 + 1 - offset, color, false);
        }
    }

    private static final class BackButton extends Button {
        private BackButton() {
            noText();
        }

        @Override
        public void drawBackgroundAdditional(GUIContext context) {
            IGuiTexture texture = isFocused() ? AETextures.icon(ECOIcon.TAB_BUTTON_BACKGROUND_FOCUS)
                : AETextures.icon(ECOIcon.TAB_BUTTON_BACKGROUND);
            float size = isFocused() ? 22 : 20;
            context.drawTexture(texture, getPositionX(), getPositionY(), size, size);
        }

        @Override
        public void drawContents(GUIContext context) {
            super.drawContents(context);
            context.drawTexture(AETextures.icon(ECOIcon.BACK), getPositionX() + 2, getPositionY() + 1, 16, 16);
        }
    }

    private static final class IntegerField extends TextField {
        @Override
        public void drawBackgroundTexture(GUIContext context) {
            IGuiTexture texture = !isActive() ? NETextures.PRIORITY_TEXT_FIELD_DISABLED
                : isFocused() ? NETextures.PRIORITY_TEXT_FIELD_FOCUS : NETextures.PRIORITY_TEXT_FIELD;
            context.drawTexture(texture, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        }
    }
}
