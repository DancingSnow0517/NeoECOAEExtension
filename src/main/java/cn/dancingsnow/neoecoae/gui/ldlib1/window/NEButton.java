package cn.dancingsnow.neoecoae.gui.ldlib1.window;

import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Textures;
import cn.dancingsnow.neoecoae.gui.ldlib1.NELDLib1Theme;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Proper layered button for LDLib1. Layers a ButtonWidget (background, hover,
 * click) underneath a LabelWidget/ImageWidget (text or icon) so the text is
 * ALWAYS rendered on top and never covered by the button background.
 *
 * <p>Usage replaces all {@code TextTexture.setBackgroundTexture(BUTTON)}
 * patterns in builder and tool bars.</p>
 */
public class NEButton extends NEWidgetGroup {

    private final ButtonWidget btn;
    private LabelWidget label;
    private int textColor;

    private NEButton(int x, int y, int w, int h,
                     ButtonWidget button, LabelWidget textLabel, int textColor) {
        super(x, y, w, h);
        this.btn = button;
        this.label = textLabel;
        this.textColor = textColor;
        addWidget(btn);
        if (textLabel != null) {
            addWidget(textLabel);
        }
    }

    /** Text button with dark text + shadow on button background. */
    public static NEButton text(int x, int y, int w, int h,
                                 Component text, int textColor,
                                 Consumer<ClickData> onClick) {
        return textWithShadow(x, y, w, h, text.getString(), textColor, true, onClick);
    }

    /** Square text button. */
    public static NEButton squareText(int x, int y, int size,
                                       Component text, int textColor,
                                       Consumer<ClickData> onClick) {
        return text(x, y, size, size, text, textColor, onClick);
    }

    /** Text button with a plain String label. */
    public static NEButton text(int x, int y, int w, int h,
                                 String text, int textColor,
                                 Consumer<ClickData> onClick) {
        return textWithShadow(x, y, w, h, text, textColor, true, onClick);
    }

    /** Icon button (no text label). */
    public static NEButton icon(int x, int y, int size,
                                 IGuiTexture icon,
                                 Component tooltip,
                                 Consumer<ClickData> onClick) {
        var btn = new ButtonWidget(0, 0, size, size, icon, onClick)
            .setHoverTexture(NELDLib1Textures.BUTTON_HOVER)
            .setClickedTexture(NELDLib1Textures.BUTTON_HIGHLIGHTED);
        if (tooltip != null) {
            btn.setHoverTooltips(tooltip);
        }
        return new NEButton(x, y, size, size, btn, null, 0);
    }

    private static NEButton textWithShadow(int x, int y, int w, int h,
                                            String text, int textColor,
                                            boolean shadow,
                                            Consumer<ClickData> onClick) {
        // Background-only button
        var btnBg = new ButtonWidget(0, 0, w, h,
            NELDLib1Textures.BUTTON, onClick)
            .setHoverTexture(NELDLib1Textures.BUTTON_HOVER)
            .setClickedTexture(NELDLib1Textures.BUTTON_HIGHLIGHTED);

        // Text layer on top — rough center
        var lbl = new LabelWidget(0, 0, text)
            .setTextColor(textColor)
            .setDropShadow(shadow);
        int textW = Math.max(1, w - 4);
        lbl.setSelfPosition(Math.max(1, (w - textW) / 2), Math.max(1, (h - 8) / 2));

        return new NEButton(x, y, w, h, btnBg, lbl, textColor);
    }

    /** Set hover tooltip. */
    public NEButton tooltip(Component... tips) {
        if (btn != null && tips.length > 0) {
            btn.setHoverTooltips(tips);
        }
        return this;
    }

    /** Change the text label. */
    public void setText(String text) {
        if (label != null) {
            label.setText(text);
        }
    }
}
